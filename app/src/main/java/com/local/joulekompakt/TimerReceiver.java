package com.local.joulekompakt;

import android.app.*;
import android.content.*;

/** Allows a reminder notification even if Android has reclaimed the controller service. */
public class TimerReceiver extends BroadcastReceiver {
	@Override public void onReceive(Context c, Intent intent) {
		long end = c.getSharedPreferences("joule", Context.MODE_PRIVATE).getLong("timerEnd", 0);
		if (end == 0 || System.currentTimeMillis() < end) return;
		NotificationManager nm = c.getSystemService(NotificationManager.class);
		nm.createNotificationChannel(new NotificationChannel("timer", "Cook timer", NotificationManager.IMPORTANCE_HIGH));
		PendingIntent pi = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
		nm.notify(2, new Notification.Builder(c, "timer").setSmallIcon(R.drawable.ic_joule).setContentTitle("Cook timer finished").setContentText("Joule keeps heating. Tap to open controls.").setContentIntent(pi).setAutoCancel(true).build());
	}
}
