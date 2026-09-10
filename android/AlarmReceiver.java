package com.rohit.turfwar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * AlarmReceiver
 *
 * Woken up every 15 minutes by AlarmManager (set in TurfWarBridge.startBackgroundChecks).
 * Also woken up on device boot (BOOT_COMPLETED) to re-register the alarm.
 *
 * Registered in AndroidManifest with:
 *   <receiver android:name=".AlarmReceiver" android:exported="false">
 *     <intent-filter>
 *       <action android:name="android.intent.action.BOOT_COMPLETED"/>
 *     </intent-filter>
 *   </receiver>
 */
public class AlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "TurfWarAlarm";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        Log.d(TAG, "AlarmReceiver fired — action: " + intent.getAction());

        // On boot, re-schedule the repeating alarm
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "Boot completed — re-registering alarm");
            // Use the bridge helper to re-schedule (reuse same logic)
            new TurfWarBridge(ctx).startBackgroundChecks();
            return;
        }

        // Start the background check service
        Intent serviceIntent = new Intent(ctx, LocationCheckService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Must use startForegroundService on API 26+
                ctx.startForegroundService(serviceIntent);
            } else {
                ctx.startService(serviceIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not start service: " + e.getMessage());
        }
    }
}
