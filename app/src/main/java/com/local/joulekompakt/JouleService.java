package com.local.joulekompakt;

import android.app.*;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.os.*;
import android.util.Base64;
import java.util.*;

/** All BLE operations and application state are serialized on the main looper. */
public class JouleService extends Service {
	static final UUID SERVICE = uuid("4321"), WRITE = uuid("4322"), READ = uuid("4323"), NOTIFY = uuid("4325");
	static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
	static final UUID CHANGED = UUID.fromString("00002a05-0000-1000-8000-00805f9b34fb");
	static UUID uuid(String n) { return UUID.fromString("700b" + n + "-9836-4383-a2b2-31a9098d1473"); }
	final Handler h = new Handler(Looper.getMainLooper());
	final LocalBinder binder = new LocalBinder();
	final ArrayDeque<Op> queue = new ArrayDeque<>();
	final LinkedHashMap<String, ScanResult> found = new LinkedHashMap<>();
	final Set<Integer> exchangeHandles = new HashSet<>();
	final StringBuilder log = new StringBuilder();
	final Random random = new java.security.SecureRandom();
	SharedPreferences prefs;
	BluetoothAdapter adapter; BluetoothGatt gatt;
	BluetoothGattCharacteristic write, read, notify;
	Op active;
	Runnable listener;
	boolean scanning, ready, pairing, submitting, configured, foreground;
	int generation, mtu = 23, submitHandle, commandHandle, commandType, step = -1, error;
	long feed, sequence, lastData, lastRead, lastRenew, commandSent, timerEnd;
	float temperature = Float.NaN;
	String status = "Tap Find Joule to connect.", address = "", command = "", timerText = "Timer off";
	byte[] recipient = Proto.EMPTY, candidateKey;
	float requestedTemp;
	boolean preparing, fullStart;
	long prepareAfter;
	static class Op {
		String label; BluetoothGattCharacteristic characteristic; BluetoothGattDescriptor descriptor;
		byte[] value; boolean noResponse, optional; Runnable done;
	}
	public class LocalBinder extends Binder { JouleService service() { return JouleService.this; } }
	@Override public IBinder onBind(Intent intent) { return binder; }
	@Override public void onCreate() {
		super.onCreate(); prefs = getSharedPreferences("joule", MODE_PRIVATE);
		adapter = ((BluetoothManager)getSystemService(BLUETOOTH_SERVICE)).getAdapter();
		timerEnd = prefs.getLong("timerEnd", 0);
		NotificationManager nm = getSystemService(NotificationManager.class);
		nm.createNotificationChannel(new NotificationChannel("connection", "Joule connection", NotificationManager.IMPORTANCE_LOW));
		nm.createNotificationChannel(new NotificationChannel("timer", "Cook timer", NotificationManager.IMPORTANCE_HIGH));
		h.post(tick);
	}
	@Override public int onStartCommand(Intent i, int flags, int id) {
		if (i != null && "timer_done".equals(i.getAction())) updateTimer();
		return START_NOT_STICKY;
	}
	void changed() { if (listener != null) listener.run(); }
	void note(String text) {
		log.append(new java.text.SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date())).append(" ").append(text).append('\n');
		if (log.length() > 16000) log.delete(0, log.length() - 12000);
		changed();
	}
	void status(String text) { status = text; note(text); }
	Notification notification(String channel, String title, String text) {
		PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
		return new Notification.Builder(this, channel).setSmallIcon(R.drawable.ic_joule).setContentTitle(title)
			.setContentText(text).setContentIntent(pi).setOngoing(channel.equals("connection")).setAutoCancel(channel.equals("timer")).build();
	}
	void foreground() {
		if (!foreground) {
			startForeground(1, notification("connection", "Joule Local", "Bluetooth controller · tap to open"));
			foreground = true;
		}
	}
	boolean bluetooth() {
		if (adapter == null || !adapter.isEnabled()) { status("Turn on Bluetooth, then tap Find Joule."); return false; }
		return true;
	}
	void scan() {
		if (!bluetooth()) return;
		disconnect(false); foreground(); found.clear(); scanning = true;
		status("Looking for Joule…");
		try {
			adapter.getBluetoothLeScanner().startScan(null, new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback);
			final int gen = generation;
			h.postDelayed(() -> { if (gen == generation && scanning) { stopScan(); status(found.isEmpty() ? "No Joule found. Close other Joule apps and try again." : "Choose your Joule below."); } }, 12000);
		} catch (Exception e) { scanning = false; status("Scan failed: " + e.getMessage()); }
	}
	void stopScan() {
		if (scanning) {
			try { if (adapter != null && adapter.getBluetoothLeScanner() != null) adapter.getBluetoothLeScanner().stopScan(scanCallback); } catch (Exception ignored) { }
			scanning = false; changed();
		}
	}
	final ScanCallback scanCallback = new ScanCallback() {
		@Override public void onScanResult(int type, ScanResult result) { h.post(() -> {
			if (!scanning || result.getScanRecord() == null) return;
			ScanRecord sr = result.getScanRecord(); String name = sr.getDeviceName();
			boolean matches = sr.getManufacturerSpecificData(0x0159) != null || (name != null && name.toLowerCase(Locale.US).contains("joule"))
				|| (sr.getServiceUuids() != null && sr.getServiceUuids().contains(new ParcelUuid(SERVICE)));
			if (matches) { boolean fresh = !found.containsKey(result.getDevice().getAddress()); found.put(result.getDevice().getAddress(), result); if (fresh) changed(); }
		}); }
		@Override public void onScanFailed(int code) { h.post(() -> { scanning = false; status("Bluetooth scan failed (" + code + "). Try again."); }); }
	};
	void connect(ScanResult result) {
		disconnect(false); if (!bluetooth()) return; foreground();
		address = result.getDevice().getAddress();
		byte[] ad = result.getScanRecord() == null ? null : result.getScanRecord().getManufacturerSpecificData(0x0159);
		recipient = ad != null && ad.length >= 8 ? Arrays.copyOf(ad, 8) : Proto.EMPTY;
		status("Connecting to " + address + "…");
		try {
			gatt = result.getDevice().connectGatt(this, false, callback, BluetoothDevice.TRANSPORT_LE);
			final int gen = generation;
			h.postDelayed(() -> { if (gen == generation && !configured) fail("Connection setup timed out. Tap Find Joule to retry."); }, 25000);
		} catch (Exception e) { fail("Connection failed: " + e.getMessage()); }
	}
	void disconnect(boolean user) {
		generation++; stopScan(); queue.clear(); active = null; h.removeCallbacks(opTimeout);
		ready = pairing = submitting = configured = preparing = false;
		commandHandle = commandType = 0; command = ""; submitHandle = 0; exchangeHandles.clear();
		lastData = lastRead = lastRenew = feed = sequence = 0; temperature = Float.NaN; step = -1; error = 0;
		BluetoothGatt old = gatt; gatt = null;
		if (old != null) { try { old.disconnect(); old.close(); } catch (Exception ignored) { } }
		write = read = notify = null; mtu = 23;
		if (user) { status("Disconnected. Disconnecting does not stop the Joule."); releaseForeground(); }
	}
	void releaseForeground() { if (timerEnd == 0 && gatt == null && !scanning && foreground) { stopForeground(STOP_FOREGROUND_REMOVE); foreground = false; } }
	void fail(String message) { disconnect(false); status(message); releaseForeground(); }
	void forget() {
		if (!address.isEmpty()) prefs.edit().remove("key_" + address).apply();
		disconnect(true); status("Saved key removed for this Joule. Find it again to pair.");
	}
	final BluetoothGattCallback callback = new BluetoothGattCallback() {
		@Override public void onConnectionStateChange(BluetoothGatt g, int code, int state) { h.post(() -> {
			if (g != gatt) return;
			if (code != 0 || state == BluetoothProfile.STATE_DISCONNECTED) { fail("Bluetooth disconnected (" + code + "). Joule state unknown; reconnect to check."); return; }
			if (state == BluetoothProfile.STATE_CONNECTED) { status("Connected. Reading services…"); if (!g.discoverServices()) fail("Cannot discover Bluetooth services."); }
		}); }
		@Override public void onServicesDiscovered(BluetoothGatt g, int code) { h.post(() -> {
			if (g != gatt) return;
			BluetoothGattService s = g.getService(SERVICE);
			if (code != 0 || s == null) { fail("Original Joule service not found. This build is for CS10001 / CS20001."); return; }
			write = s.getCharacteristic(WRITE); read = s.getCharacteristic(READ); notify = s.getCharacteristic(NOTIFY);
			if (write == null || read == null || notify == null) { fail("Required Joule Bluetooth characteristics missing."); return; }
			// Android supports long characteristic writes if the peripheral remains at MTU 23.
			if (!g.requestMtu(185)) setupNotifications();
		}); }
		@Override public void onMtuChanged(BluetoothGatt g, int size, int code) { h.post(() -> { if (g == gatt && !configured) { if (code == 0) mtu = size; note("ATT MTU " + mtu); setupNotifications(); } }); }
		@Override public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor d, int code) { h.post(() -> { if (g == gatt && active != null && active.descriptor == d) complete(code); }); }
		@Override public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic c, int code) { h.post(() -> { if (g == gatt && active != null && active.characteristic == c && active.value != null) complete(code); }); }
		@Override public void onCharacteristicRead(BluetoothGatt g, BluetoothGattCharacteristic c, int code) {
			byte[] data = c.getValue() == null ? Proto.EMPTY : c.getValue().clone();
			h.post(() -> {
				if (g != gatt) return;
				if (code == 0 && data.length > 0) receive(data);
				if (active != null && active.characteristic == c && active.value == null) complete(code);
			});
		}
		@Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c) {
			byte[] data = c.getValue() == null ? Proto.EMPTY : c.getValue().clone();
			h.post(() -> { if (g != gatt) return; if (c.getUuid().equals(NOTIFY)) { if (data.length > 1) receive(data); requestRead(); } });
		}
	};
	void setupNotifications() {
		if (configured) return; configured = true;
		for (BluetoothGattService s : gatt.getServices()) {
			BluetoothGattCharacteristic sc = s.getCharacteristic(CHANGED);
			if (sc != null) subscribe(sc, true, true, null);
		}
		subscribe(notify, false, false, () -> {
			Op op = new Op(); op.label = "Initial read"; op.characteristic = read; op.optional = true;
			op.done = () -> { final int gen = generation; h.postDelayed(() -> { if (gen == generation) authenticate(); }, 500); }; enqueue(op);
		});
	}
	void subscribe(BluetoothGattCharacteristic c, boolean indication, boolean optional, Runnable done) {
		if (!gatt.setCharacteristicNotification(c, true)) { if (optional) { note("Service Changed registration unavailable"); return; } fail("Cannot register Joule notifications."); return; }
		BluetoothGattDescriptor d = c.getDescriptor(CCCD);
		if (d == null) { if (!optional) fail("Joule notification descriptor missing."); return; }
		Op op = new Op(); op.label = indication ? "Service Changed indications" : "Joule notifications"; op.descriptor = d;
		op.value = indication ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE : BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
		op.optional = optional; op.done = done; enqueue(op);
	}
	void enqueue(Op op) { queue.add(op); pump(); }
	void pump() {
		if (gatt == null || active != null || queue.isEmpty()) return;
		active = queue.remove(); boolean ok;
		try {
			if (active.descriptor != null) { active.descriptor.setValue(active.value); ok = gatt.writeDescriptor(active.descriptor); }
			else if (active.value == null) ok = gatt.readCharacteristic(active.characteristic);
			else {
				active.characteristic.setWriteType(active.noResponse ? BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE : BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
				active.characteristic.setValue(active.value); ok = gatt.writeCharacteristic(active.characteristic);
			}
		} catch (Exception e) { fail("Bluetooth operation failed: " + e.getMessage()); return; }
		if (!ok) { complete(-1); return; }
		h.postDelayed(opTimeout, 12000);
	}
	final Runnable opTimeout = () -> { if (active != null) fail("Bluetooth operation timed out: " + active.label + ". Joule state unknown."); };
	void complete(int code) {
		if (active == null) return;
		Op op = active; active = null; h.removeCallbacks(opTimeout);
		if (code != 0) {
			note(op.label + " returned GATT " + code);
			if (!op.optional) { fail("Bluetooth error " + code + " during " + op.label + ". Reconnect to check Joule."); return; }
		}
		if (op.done != null) op.done.run(); pump();
	}
	void requestRead() {
		if (gatt == null || read == null) return;
		for (Op op : queue) if (op.characteristic == read && op.value == null) return;
		Op op = new Op(); op.label = "Read Joule"; op.characteristic = read; op.optional = false;
		lastRead = SystemClock.elapsedRealtime(); enqueue(op);
	}
	int handle() { return random.nextInt(Integer.MAX_VALUE - 1) + 1; }
	int send(int type, byte[] body) { return send(type, body, Proto.EMPTY, Proto.EMPTY, false, false); }
	int send(int type, byte[] body, byte[] sender, byte[] dest, boolean noResponse, boolean optional) {
		int id = handle(); Op op = new Op(); op.label = "Message " + type; op.characteristic = write;
		op.value = Proto.envelope(id, type, body, sender, dest); op.noResponse = noResponse; op.optional = optional;
		note("Send " + type + " (" + op.value.length + " bytes, handle " + id + ")"); enqueue(op); return id;
	}
	void authenticate() {
		String saved = prefs.getString("key_" + address, "");
		if (!saved.isEmpty()) {
			try { candidateKey = Base64.decode(saved, Base64.NO_WRAP); submit(false); return; } catch (IllegalArgumentException ignored) { }
		}
		pair();
	}
	void pair() {
		pairing = true; submitting = false; exchangeHandles.clear();
		status("Press the button on top of your Joule now. Pairing: 60 seconds.");
		exchangeHandles.add(send(120, Proto.EMPTY));
		final int gen = generation;
		h.postDelayed(() -> {
			if (gen != generation || !pairing) return;
			if ((write.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0)
				exchangeHandles.add(send(120, Proto.EMPTY, Proto.EMPTY, Proto.EMPTY, true, true));
			byte[] mac = new byte[6]; String[] parts = address.split(":");
			for (int i = 0; i < 6; i++) mac[i] = (byte)Integer.parseInt(parts[i], 16);
			exchangeHandles.add(send(120, Proto.EMPTY, Proto.EMPTY, mac, false, true));
			if (recipient.length == 8) exchangeHandles.add(send(120, Proto.EMPTY, new byte[]{(byte)0xaa,(byte)0xbb,(byte)0xaa,(byte)0xbb,(byte)0xaa,(byte)0xbb,(byte)0xaa,(byte)0xbb}, recipient, false, true));
		}, 3000);
		h.postDelayed(() -> { if (gen == generation && pairing) fail("Pairing timed out. Find Joule again, then press its top button when asked."); }, 60000);
	}
	void submit(boolean full) {
		pairing = false; submitting = true; status("Authenticating with Joule…");
		submitHandle = full ? send(130, Proto.bytes(1, candidateKey), new byte[]{(byte)0xaa,(byte)0xbb,(byte)0xaa,(byte)0xbb,(byte)0xaa,(byte)0xbb,(byte)0xaa,(byte)0xbb}, recipient, false, false)
			: send(130, Proto.bytes(1, candidateKey));
		final int gen = generation, id = submitHandle;
		h.postDelayed(() -> { if (gen == generation && submitting && submitHandle == id) { if (!full && recipient.length == 8) submit(true); else pair(); } }, 10000);
	}
	void renew() { if (ready) { send(70, Proto.number(1, 1)); lastRenew = SystemClock.elapsedRealtime(); } }
	void receive(byte[] raw) {
		try {
			List<Proto.Field> outer = Proto.fields(raw); int replyHandle = 0;
			for (Proto.Field f : outer) if (f.id == 1 && f.wire == 5) replyHandle = (int)f.value;
			for (Proto.Field f : outer) {
				if (f.wire != 2 || f.id < 18) continue;
				List<Proto.Field> inner = Proto.fields(f.data);
				if (f.id != 90) note("Receive " + f.id + " (handle " + replyHandle + ")");
				if (f.id == 121 && pairing && exchangeHandles.contains(replyHandle)) {
					long result = Proto.number(inner, 2, 0); byte[] key = Proto.data(inner, 1);
					if (result == 0 && key != null && key.length > 0 && key.length <= 256) { candidateKey = key; submit(false); }
					else status("Pairing response " + result + ". Press Joule's top button.");
				} else if (f.id == 131 && submitting && replyHandle == submitHandle) {
					long result = Proto.number(inner, 1, 0);
					if (result == 0) {
						submitting = false; ready = true;
						prefs.edit().putString("key_" + address, Base64.encodeToString(candidateKey, Base64.NO_WRAP)).apply();
						status("Connected. Waiting for water temperature…"); renew();
					} else { note("Saved key rejected (" + result + ")"); pair(); }
				} else if (f.id == 90 && ready) {
					feed = Proto.number(inner, 1, 0); sequence = Proto.number(inner, 2, 0);
					float t = Proto.temperature(inner); if (Float.isFinite(t) && t >= -10 && t <= 110) temperature = t; else temperature = Float.NaN;
					step = (int)Proto.number(inner, 11, 0); error = (int)Proto.number(inner, 4, 0); lastData = SystemClock.elapsedRealtime();
					status = error != 0 ? "Joule reports an error (" + error + "). Check the appliance." : "Connected · " + stateName();
					if (preparing && lastData > prepareAfter) {
						preparing = false;
						if (error != 0 || step == 5 || !Float.isFinite(temperature)) { commandType = 0; command = "Cannot start: invalid temperature or Joule error."; changed(); return; }
						send(152, Proto.EMPTY); final int gen = generation;
						h.postDelayed(() -> { if (gen == generation && commandType == 50 && commandHandle == 0) sendStart(); }, 500);
					}
					changed();
				} else if ((f.id == 51 || f.id == 61) && replyHandle == commandHandle && commandType == f.id - 1) {
					long result = Proto.number(inner, 1, 0); int type = commandType;
					commandType = commandHandle = 0;
					command = result == 0 ? (type == 50 ? "Joule accepted the temperature / start command." : "Joule confirmed STOP.") : "Joule rejected " + (type == 50 ? "START" : "STOP") + " (code " + result + ").";
					note(command); renew();
				}
			}
		} catch (IllegalArgumentException e) { note("Ignored unreadable Bluetooth message (" + raw.length + " bytes)"); }
	}
	String stateName() {
		switch(step) { case 0: return "Idle"; case 1: return "Heating"; case 2: return "Ready for food"; case 3: return "Cooking"; case 4: return "Cook complete"; case 5: return "Device error"; default: return "State unknown"; }
	}
	boolean fresh() { return ready && lastData != 0 && SystemClock.elapsedRealtime() - lastData < 15000 && Float.isFinite(temperature); }
	void startCook(float fahrenheit, boolean full) {
		if (!fresh() || error != 0 || step == 5 || commandType != 0) { status("Wait for a fresh temperature and clear any Joule error before starting."); return; }
		if (!Float.isFinite(fahrenheit) || fahrenheit < 32 || fahrenheit > 208.4f) { status("Enter a temperature from 32 to 208.4°F."); return; }
		requestedTemp = (fahrenheit - 32f) * 5f / 9f; fullStart = full;
		commandType = 50; commandHandle = 0; preparing = true; prepareAfter = SystemClock.elapsedRealtime(); commandSent = prepareAfter;
		command = "Refreshing Joule state before START…"; renew(); changed();
	}
	void sendStart() {
		if (!fresh() || error != 0 || step == 5) { commandType = 0; command = "START cancelled: fresh, valid Joule state is unavailable."; changed(); return; }
		String id = fullStart ? UUID.randomUUID().toString().replace("-", "") : null;
		commandHandle = send(50, Proto.startBody(requestedTemp, feed, sequence, id));
		commandSent = SystemClock.elapsedRealtime(); command = "START sent; waiting for Joule confirmation…"; changed();
	}
	void stopCook() {
		if (!ready) { status("Not connected. Use the Joule's physical button to stop it."); return; }
		preparing = false;
		// A queued but not yet transmitted START must never follow STOP.
		queue.removeIf(op -> "Message 50".equals(op.label));
		commandType = 60; commandHandle = send(60, Proto.EMPTY); commandSent = SystemClock.elapsedRealtime();
		command = "STOP sent; waiting for Joule confirmation…"; changed();
	}
	void toggleTimer(int minutes) {
		if (timerEnd != 0) { timerEnd = 0; timerText = "Timer cancelled"; cancelAlarm(); }
		else {
			if (minutes < 1 || minutes > 5999) { status("Timer must be 1 to 5999 minutes."); return; }
			foreground(); timerEnd = System.currentTimeMillis() + minutes * 60000L;
			AlarmManager am = getSystemService(AlarmManager.class);
			am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timerEnd, timerIntent());
		}
		prefs.edit().putLong("timerEnd", timerEnd).apply(); updateTimer(); changed(); releaseForeground();
	}
	PendingIntent timerIntent() { return PendingIntent.getBroadcast(this, 3, new Intent(this, TimerReceiver.class).setAction("timer_done"), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE); }
	void cancelAlarm() { getSystemService(AlarmManager.class).cancel(timerIntent()); }
	void updateTimer() {
		if (timerEnd == 0) return;
		long remaining = timerEnd - System.currentTimeMillis();
		if (remaining <= 0) {
			timerEnd = 0; prefs.edit().putLong("timerEnd", 0).apply(); timerText = "TIMER DONE — Joule keeps heating";
			getSystemService(NotificationManager.class).notify(2, notification("timer", "Cook timer finished", "Joule keeps heating. Tap to open controls."));
			cancelAlarm(); note(timerText); releaseForeground();
		} else { long seconds = (remaining + 999) / 1000; timerText = String.format(Locale.US, "%02d:%02d:%02d remaining", seconds / 3600, seconds / 60 % 60, seconds % 60); }
	}
	final Runnable tick = new Runnable() { @Override public void run() {
		long now = SystemClock.elapsedRealtime(); updateTimer();
		if (gatt != null && configured && (ready || pairing || submitting) && now - lastRead >= 4000) requestRead();
		if (ready && now - lastRenew > 25000 && commandType == 0) renew();
		if (commandType != 0 && now - commandSent > 15000) {
			preparing = false; commandType = commandHandle = 0;
			command = "No command confirmation. Joule state uncertain; check the appliance."; note(command);
		}
		changed(); h.postDelayed(this, 1000);
	} };
	@Override public void onDestroy() { h.removeCallbacksAndMessages(null); disconnect(false); super.onDestroy(); }
}
