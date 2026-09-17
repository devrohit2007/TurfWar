package com.rohit.turfwar;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.app.NotificationCompat;

/**
 * Keeps the active-run notification alive while TurfWar is in the background.
 * JavaScript supplies the authoritative distance and active elapsed time.
 * The service advances the displayed timer between JS updates, so the timer
 * remains live even when WebView timers are throttled in the background.
 */
public class RunTrackingService extends Service {
    public static final String ACTION_START = "com.rohit.turfwar.RUN_START";
    public static final String ACTION_UPDATE = "com.rohit.turfwar.RUN_UPDATE";
    public static final String ACTION_STOP = "com.rohit.turfwar.RUN_STOP";

    private static final String TAG = "TurfWarRunSvc";
    private static final String CHANNEL_ID = TurfWarBridge.RUN_CHANNEL_ID;
    private static final int NOTIF_ID = TurfWarBridge.RUN_NOTIF_ID;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private String mode = "Running";
    private long elapsedSeconds = 0L;
    private double distanceMeters = 0d;
    private boolean paused = false;
    private long lastSyncElapsed = 0L;
    // Chronometer base: System.currentTimeMillis() - elapsedSeconds*1000 at last JS sync.
    // Android ticks the timer natively from this — no nm.notify() every second needed.
    private long whenBase = 0L;

    @Override public void onCreate() {
        super.onCreate();
        ensureChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();

        if (ACTION_START.equals(action)) {
            mode = intent.getStringExtra("mode");
            if (mode == null || mode.isEmpty()) mode = "Running";
            elapsedSeconds = 0L;
            distanceMeters = 0d;
            paused = false;
            lastSyncElapsed = SystemClock.elapsedRealtime();
            whenBase = System.currentTimeMillis(); // elapsed=0, so base=now
            startForegroundCompat();
            postRunNotification();
            return START_NOT_STICKY;
        }

        if (ACTION_UPDATE.equals(action)) {
            mode = intent.getStringExtra("mode");
            if (mode == null || mode.isEmpty()) mode = "Running";
            elapsedSeconds = Math.max(0L, intent.getLongExtra("elapsed", elapsedSeconds));
            distanceMeters = Math.max(0d, intent.getDoubleExtra("distance", distanceMeters));
            paused = intent.getBooleanExtra("paused", false);
            lastSyncElapsed = SystemClock.elapsedRealtime();
            whenBase = System.currentTimeMillis() - elapsedSeconds * 1000L; // re-sync chronometer
            startForegroundCompat(); // always call — safe to repeat, fixes crash when service was dead
            postRunNotification();
            return START_NOT_STICKY;
        }

        if (ACTION_STOP.equals(action)) {
            stopRunService();
            return START_NOT_STICKY;
        }

        return START_NOT_STICKY;
    }

    private void postRunNotification() {
        String km = String.format(java.util.Locale.US, "%.2f km", distanceMeters / 1000d);
        String pace = distanceMeters > 50d
                ? formatPace(distanceMeters, elapsedSeconds) + " min/km"
                : "--:-- min/km";
        String title = paused ? "Paused · " + mode : mode;

        Intent tapIntent = new Intent(this, MainActivity.class);
        tapIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int piFlags = Build.VERSION.SDK_INT >= 23
                ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pi = PendingIntent.getActivity(this, 0, tapIntent, piFlags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("TurfWar · " + title)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        if (!paused && whenBase != 0L) {
            // OS ticks the timer natively — smooth, no flicker, no nm.notify() every second
            builder.setUsesChronometer(true)
                   .setShowWhen(true)
                   .setWhen(whenBase)
                   .setContentText(km + "  •  " + pace);
        } else {
            // Paused — show frozen elapsed time as plain text
            builder.setUsesChronometer(false)
                   .setShowWhen(false)
                   .setContentText(formatTime(elapsedSeconds) + "  •  " + km + "  •  " + pace);
        }

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_ID, builder.build());
    }

    private void startForegroundCompat() {
        Notification initial = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("TurfWar · " + mode)
                .setContentText("Starting live run tracker…")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
        try {
            applyForeground(initial);
        } catch (Exception e) {
            Log.e(TAG, "Could not enter foreground", e);
            stopRunService();
        }
    }

    /** Calls startForeground() with the correct type for each API level.
     *  Using startForeground() for updates (instead of nm.notify) replaces
     *  the notification in-place — no heads-up animation every second. */
    private void applyForeground(Notification notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                    | ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIF_ID, notification);
        }
    }

    private void stopRunService() {
        handler.removeCallbacksAndMessages(null);
        lastSyncElapsed = 0L;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NOTIF_ID);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE); // non-deprecated form (API 24+)
        } else {
            stopForeground(true);
        }
        stopSelf();
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, TurfWarBridge.RUN_CHANNEL_NM,
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Live TurfWar run timer, distance and pace");
            ch.setSound(null, null);
            ch.enableVibration(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private static String formatTime(long secs) {
        long h = secs / 3600L;
        long m = (secs % 3600L) / 60L;
        long s = secs % 60L;
        if (h > 0) return String.format(java.util.Locale.US, "%02d:%02d:%02d", h, m, s);
        return String.format(java.util.Locale.US, "%02d:%02d", m, s);
    }

    private static String formatPace(double meters, long secs) {
        if (meters <= 50d || secs <= 0L) return "--:--";
        double secPerKm = secs / (meters / 1000d);
        long mins = (long) (secPerKm / 60d);
        long sec = Math.round(secPerKm - mins * 60d);
        if (sec >= 60) { mins++; sec = 0; }
        return String.format(java.util.Locale.US, "%02d:%02d", mins, sec);
    }
}
