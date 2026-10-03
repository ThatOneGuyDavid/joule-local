package com.local.joulekompakt;

import android.Manifest;
import android.app.*;
import android.bluetooth.le.ScanResult;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
	JouleService service;
	boolean bound, fullFormat;
	TextView connectionText, current, command, timer;
	EditText target, minutes;
	Button find, disconnect, start, stop, timerButton;
	LinearLayout devices;
	String deviceSignature = "";
	android.content.SharedPreferences prefs;
	final ServiceConnection connection = new ServiceConnection() {
		@Override public void onServiceConnected(ComponentName n, IBinder b) { service = ((JouleService.LocalBinder)b).service(); service.listener = () -> render(); render(); }
		@Override public void onServiceDisconnected(ComponentName n) { service = null; connectionText.setText("Controller service stopped. Reopen the app to reconnect."); start.setEnabled(false); stop.setEnabled(false); }
	};
	@Override public void onCreate(Bundle saved) {
		super.onCreate(saved); prefs = getSharedPreferences("joule", MODE_PRIVATE);
		fullFormat = prefs.getBoolean("fullFormat", false);
		ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(Color.WHITE);
		LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(12), dp(8), dp(12), dp(12)); scroll.addView(root);
		scroll.setOnApplyWindowInsetsListener((v, insets) -> { android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()); v.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets; });
		TextView title = text("JOULE LOCAL", 24, true); root.addView(title);
		connectionText = text("Tap Find Joule to connect.", 15, false); root.addView(connectionText);
		LinearLayout links = row(); root.addView(links);
		find = button("Find Joule", () -> { if (permissions() && service != null) service.scan(); }); weighted(links, find, 1);
		disconnect = button("Disconnect", () -> { if (service != null) service.disconnect(true); }); weighted(links, disconnect, 1);
		devices = new LinearLayout(this); devices.setOrientation(LinearLayout.VERTICAL); root.addView(devices);
		line(root);
		current = text("Water: — °F", 30, true); root.addView(current);
		root.addView(text("TARGET °F", 14, true));
		LinearLayout temperatureRow = row(); root.addView(temperatureRow);
		target = edit(prefs.getString("target", "135.0"), true); target.setId(1001);
		weighted(temperatureRow, button("−", () -> adjust(target, -1, 32, 208.4, true)), 1);
		weighted(temperatureRow, target, 2);
		weighted(temperatureRow, button("+", () -> adjust(target, 1, 32, 208.4, true)), 1);
		LinearLayout controls = row(); root.addView(controls);
		start = button("START / SET", () -> start()); weighted(controls, start, 1);
		stop = button("STOP", () -> { if (service != null) service.stopCook(); }); weighted(controls, stop, 1);
		command = text("", 14, false); root.addView(command);
		line(root);
		root.addView(text("PHONE TIMER · minutes", 14, true));
		LinearLayout timerRow = row(); root.addView(timerRow);
		minutes = edit(prefs.getString("minutes", "120"), false); minutes.setId(1002);
		weighted(timerRow, button("−", () -> adjust(minutes, -5, 1, 5999, false)), 1);
		weighted(timerRow, minutes, 2);
		weighted(timerRow, button("+", () -> adjust(minutes, 5, 1, 5999, false)), 1);
		timer = text("Timer off", 18, true); root.addView(timer);
		timerButton = button("Start timer", () -> {
			if (!permissions() || service == null) return;
			if (service.timerEnd != 0) { service.toggleTimer(1); return; }
			try { int n = Integer.parseInt(minutes.getText().toString()); service.toggleTimer(n); saveInputs(); }
			catch (NumberFormatException e) { minutes.setError("Enter whole minutes, 1–5999"); }
		}); root.addView(timerButton);
		root.addView(text("Start the timer when you add food. It does not stop the heater.", 13, false));
		root.addView(button("More / diagnostics", () -> more()));
		setContentView(scroll);
		Intent intent = new Intent(this, JouleService.class); startService(intent); bound = bindService(intent, connection, BIND_AUTO_CREATE);
	}
	void start() {
		if (service == null) return;
		try {
			float f = Float.parseFloat(target.getText().toString());
			if (!Float.isFinite(f) || f < 32 || f > 208.4f) { target.setError("32–208.4°F"); return; }
			saveInputs(); ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(target.getWindowToken(), 0);
			target.clearFocus(); service.startCook(f, fullFormat);
		} catch (NumberFormatException e) { target.setError("Enter a temperature"); }
	}
	boolean permissions() {
		ArrayList<String> missing = new ArrayList<>();
		for (String p : new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}) if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) missing.add(p);
		if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED && !prefs.getBoolean("notificationAsked", false)) {
			missing.add(Manifest.permission.POST_NOTIFICATIONS); prefs.edit().putBoolean("notificationAsked", true).apply();
		}
		if (!missing.isEmpty()) { requestPermissions(missing.toArray(new String[0]), 10); return false; }
		return true;
	}
	@Override public void onRequestPermissionsResult(int code, String[] permissions, int[] grants) {
		super.onRequestPermissionsResult(code, permissions, grants);
		if (code == 10) {
			if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
				Toast.makeText(this, "Permission saved. Tap your button again.", Toast.LENGTH_SHORT).show();
			else new AlertDialog.Builder(this).setMessage("Nearby devices permission is needed for Bluetooth. You can enable it in Android Settings → Apps → Joule Local → Permissions.").setPositiveButton("OK", null).show();
		}
	}
	void render() {
		if (service == null) return;
		JouleService s = service;
		setText(connectionText, s.status);
		String value = Float.isFinite(s.temperature) ? String.format(Locale.US, "Water: %.1f°F%s", s.temperature * 9f / 5f + 32f, s.fresh() ? "" : " (stale)") : "Water: — °F";
		setText(current, value); setText(command, s.command); command.setVisibility(s.command.isEmpty() ? View.GONE : View.VISIBLE);
		start.setEnabled(s.fresh() && s.error == 0 && s.step != 5 && s.commandType == 0);
		stop.setEnabled(s.ready); disconnect.setEnabled(s.gatt != null || s.scanning);
		find.setEnabled(!s.scanning); setText(find, s.scanning ? "Searching…" : "Find Joule");
		setText(timer, s.timerText); setText(timerButton, s.timerEnd == 0 ? "Start timer" : "Cancel timer");
		String signature = s.gatt == null ? s.found.keySet().toString() : "connected";
		if (!deviceSignature.equals(signature)) {
			deviceSignature = signature; devices.removeAllViews();
			if (s.gatt == null) for (ScanResult result : s.found.values()) {
				String name = result.getScanRecord() == null ? null : result.getScanRecord().getDeviceName();
				String label = (name == null ? "Joule" : name) + " · " + result.getDevice().getAddress();
				Button b = button(label, () -> { if (permissions()) s.connect(result); }); b.setTextSize(13); devices.addView(b);
			}
		}
	}
	void more() {
		String[] options = {"Copy diagnostic log", "View diagnostic log", "Forget selected Joule's key", "Full start format: " + (fullFormat ? "ON" : "OFF"), "About / first test"};
		new AlertDialog.Builder(this).setTitle("Joule Local v0.1").setItems(options, (d, which) -> {
			if (which == 0) { ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Joule diagnostics", diagnostics())); Toast.makeText(this, "Log copied (pairing keys excluded)", Toast.LENGTH_SHORT).show(); }
			if (which == 1) { TextView t = text(diagnostics(), 12, false); t.setTextIsSelectable(true); t.setPadding(dp(12), dp(12), dp(12), dp(12)); ScrollView sc = new ScrollView(this); sc.addView(t); new AlertDialog.Builder(this).setTitle("Diagnostics").setView(sc).setPositiveButton("Close", null).show(); }
			if (which == 2) new AlertDialog.Builder(this).setMessage("Remove the saved pairing key for the selected Joule? You'll need its top button to pair again.").setNegativeButton("Cancel", null).setPositiveButton("Forget", (x,y) -> { if (service != null) service.forget(); }).show();
			if (which == 3) { fullFormat = !fullFormat; prefs.edit().putBoolean("fullFormat", fullFormat).apply(); Toast.makeText(this, "Full start format " + (fullFormat ? "on" : "off"), Toast.LENGTH_SHORT).show(); }
			if (which == 4) new AlertDialog.Builder(this).setTitle("First hardware test").setMessage("Original ChefSteps Joule CS10001 / CS20001.\n\n1. Fill a pot within Joule's marked water limits. Close any other Joule app.\n2. Find Joule, select it, then press its top button when asked.\n3. Wait for a live water temperature, choose a target, then START / SET.\n4. Check that STOP is confirmed and the unit stops.\n\nThe phone timer is a reminder only. It never stops the heater and its alert may be delayed by phone sleep settings. Allow notifications for timer alerts.\n\nDisconnecting or closing this app does not stop Joule.\n\nNo account, Internet permission, cloud or Google services. Pairing keys stay in private app storage; backups are disabled.\n\nProtocol derived from acato/ha-joule (Apache 2.0). This independent app is not affiliated with Breville.\n\nFull start format adds the cook-session metadata; try it only if the default START is rejected. Hardware operation of this Android port is awaiting your test.").setPositiveButton("OK", null).show();
		}).show();
	}
	String diagnostics() { return "Joule Local v0.1\nAndroid " + Build.VERSION.RELEASE + " / " + Build.MODEL + "\n" + (service == null ? "Service unavailable" : "MTU " + service.mtu + "\n" + service.log.toString()); }
	void saveInputs() { prefs.edit().putString("target", target.getText().toString()).putString("minutes", minutes.getText().toString()).apply(); }
	void adjust(EditText field, double delta, double min, double max, boolean decimal) {
		try { double n = Double.parseDouble(field.getText().toString()); if (!Double.isFinite(n)) return; n = Math.max(min, Math.min(max, n + delta)); field.setText(decimal ? String.format(Locale.US, "%.1f", n) : Integer.toString((int)n)); } catch (NumberFormatException e) { field.setError("Enter a number"); }
	}
	TextView text(String text, int size, boolean bold) { TextView v = new TextView(this); v.setText(text); v.setTextSize(size); v.setTextColor(Color.BLACK); if (bold) v.setTypeface(null, Typeface.BOLD); v.setPadding(0, dp(3), 0, dp(3)); return v; }
	EditText edit(String value, boolean decimal) {
		EditText v = new EditText(this); v.setText(value); v.setSingleLine(true); v.setTextSize(24); v.setTextColor(Color.BLACK); v.setGravity(Gravity.CENTER);
		v.setInputType(InputType.TYPE_CLASS_NUMBER | (decimal ? InputType.TYPE_NUMBER_FLAG_DECIMAL : 0)); v.setSelectAllOnFocus(true); v.setMinimumHeight(dp(48)); return v;
	}
	Button button(String label, Runnable action) {
		Button b = new Button(this); b.setText(label); b.setTextSize(15); b.setAllCaps(false); b.setTextColor(new android.content.res.ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{0xff666666,Color.BLACK}));
		b.setMinHeight(dp(46)); b.setMinimumHeight(dp(46)); b.setPadding(dp(6), dp(4), dp(6), dp(4));
		GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.WHITE); bg.setStroke(dp(2), Color.BLACK); bg.setCornerRadius(dp(5)); b.setBackground(bg);
		LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(46)); p.setMargins(dp(2), dp(4), dp(2), dp(4)); b.setLayoutParams(p); b.setOnClickListener(v -> action.run()); return b;
	}
	LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
	void weighted(LinearLayout row, View v, int weight) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), weight); p.setMargins(dp(2), dp(3), dp(2), dp(3)); row.addView(v, p); }
	void line(LinearLayout root) { View v = new View(this); v.setBackgroundColor(Color.BLACK); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(1)); p.setMargins(0, dp(7), 0, dp(7)); root.addView(v, p); }
	int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }
	void setText(TextView view, String text) { if (!view.getText().toString().equals(text)) view.setText(text); }
	@Override protected void onPause() { saveInputs(); if (service != null) service.listener = null; super.onPause(); }
	@Override protected void onResume() { super.onResume(); if (service != null) { service.listener = () -> render(); render(); } }
	@Override protected void onDestroy() { if (service != null) service.listener = null; if (bound) unbindService(connection); super.onDestroy(); }
}
