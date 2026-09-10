package com.rohit.turfwar;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.app.AlarmManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.webkit.JavascriptInterface;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * TurfWarBridge — JavaScript ↔ Android bridge.
 *
 * Attached in MainActivity with:
 *   binding.webview1.addJavascriptInterface(new TurfWarBridge(this), "Android");
 *
 * JS calls these as: Android.methodName(args)
 */
public class TurfWarBridge {

    static final String PREFS       = "TurfWarPrefs";
    static final String CHANNEL_ID  = "turfwar_alerts";
    static final String CHANNEL_NM  = "TurfWar Alerts";
    private static final String TAG = "TurfWarBridge";

    private final Context ctx;

    public TurfWarBridge(Context ctx) {
        this.ctx = ctx;
        createChannel();
    }

    /* ================================================================
       DEVICE ID (now the Firebase UID, saved so background service
       can query Firestore without needing WebView open)
    ================================================================ */
    @JavascriptInterface
    public void saveDeviceId(String deviceId) {
        prefs().edit().putString("deviceId", deviceId).apply();
    }

    @JavascriptInterface
    public String getDeviceId() {
        return prefs().getString("deviceId", "");
    }

    @JavascriptInterface
    public void saveProjectId(String projectId) {
        prefs().edit().putString("projectId", projectId).apply();
    }

    /* ================================================================
       STEAL NOTIFICATION — called by JS real-time listener
    ================================================================ */
    @JavascriptInterface
    public void showStealNotification(String stolenByName, String areaStr) {
        String body = stolenByName + " just grabbed " + areaStr + "m² of your turf!";
        showNotification("Territory Stolen!", body, 1);
    }

    @JavascriptInterface
    public void showNotification(String title, String body) {
        showNotification(title, body, (int) (System.currentTimeMillis() % 10000));
    }

    private void showNotification(String title, String body, int id) {
        Intent intent = new Intent(ctx, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        int piFlags = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
        PendingIntent pi = PendingIntent.getActivity(ctx, id, intent, piFlags);

        NotificationCompat.Builder nb = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setVibrate(new long[]{0, 300, 150, 300});

        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(id, nb.build());
    }

    /* ================================================================
       VIBRATE — feedback when territory is claimed
    ================================================================ */
    @JavascriptInterface
    public void vibrateDevice() {
        Vibrator v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            //noinspection deprecation
            v.vibrate(250);
        }
    }

    /* ================================================================
       SAVE SHARE-CARD IMAGE TO GALLERY
       Accepts a base64 PNG data URL (canvas.toDataURL('image/png'))
       Handles both scoped storage (Android 10+) and legacy (Android 8-9)
    ================================================================ */
    @JavascriptInterface
    public void saveImageToGallery(String base64Png) {
        try {
            byte[] bytes = decodeBase64(base64Png);
            if (bytes == null) { Log.w(TAG, "saveImageToGallery: decode failed"); return; }

            String filename = "TurfWar_" + System.currentTimeMillis() + ".png";

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // ── Android 10+ — MediaStore, no permission needed ──
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
                cv.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                cv.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TurfWar");
                cv.put(MediaStore.Images.Media.IS_PENDING, 1);

                ContentResolver resolver = ctx.getContentResolver();
                Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);

                if (uri != null) {
                    try (OutputStream os = resolver.openOutputStream(uri)) {
                        if (os != null) os.write(bytes);
                    }
                    cv.clear();
                    cv.put(MediaStore.Images.Media.IS_PENDING, 0);
                    resolver.update(uri, cv, null, null);
                }
            } else {
                // ── Android 8–9 — direct write + MediaScanner ──
                // Requires WRITE_EXTERNAL_STORAGE granted at runtime
                File dir = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES), "TurfWar");
                if (!dir.exists()) //noinspection ResultOfMethodCallIgnored
                    dir.mkdirs();
                File file = new File(dir, filename);
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(bytes);
                }
                MediaScannerConnection.scanFile(ctx,
                        new String[]{file.getAbsolutePath()},
                        new String[]{"image/png"}, null);
            }
        } catch (Exception e) {
            Log.e(TAG, "saveImageToGallery failed: " + e.getMessage());
        }
    }

    /* ================================================================
       SHARE IMAGE — opens Android's native share sheet
       Saves to app cache first, shares via FileProvider content:// URI
       (required — raw file:// URIs are blocked on Android 7+)
    ================================================================ */
    @JavascriptInterface
    public void shareImage(String base64Png) {
        try {
            byte[] bytes = decodeBase64(base64Png);
            if (bytes == null) { Log.w(TAG, "shareImage: decode failed"); return; }

            File cacheDir = new File(ctx.getCacheDir(), "images");
            if (!cacheDir.exists()) //noinspection ResultOfMethodCallIgnored
                cacheDir.mkdirs();
            File file = new File(cacheDir, "share_" + System.currentTimeMillis() + ".png");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(bytes);
            }

            Uri uri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", file);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/png");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Intent chooser = Intent.createChooser(shareIntent, "Share Territory");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(chooser);
        } catch (Exception e) {
            Log.e(TAG, "shareImage failed: " + e.getMessage());
        }
    }

    /* ================================================================
       BACKGROUND CHECKS — AlarmManager every 15 minutes
    ================================================================ */
    @JavascriptInterface
    public void startBackgroundChecks() {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(ctx, AlarmReceiver.class);
        int piFlags = Build.VERSION.SDK_INT >= 23
                ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, intent, piFlags);

        long interval = 15 * 60 * 1000L;
        long first    = System.currentTimeMillis() + 60_000L;
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, first, interval, pi);
    }

    @JavascriptInterface
    public void stopBackgroundChecks() {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent intent = new Intent(ctx, AlarmReceiver.class);
        int piFlags = Build.VERSION.SDK_INT >= 23
                ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_NO_CREATE
                : PendingIntent.FLAG_NO_CREATE;
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, intent, piFlags);
        if (pi != null) am.cancel(pi);
    }

    /* ================================================================
       HELPERS
    ================================================================ */
    private byte[] decodeBase64(String data) {
        try {
            // Strip "data:image/png;base64," prefix if present
            String pure = data.contains(",") ? data.substring(data.indexOf(',') + 1) : data;
            return Base64.decode(pure, Base64.DEFAULT);
        } catch (Exception e) {
            Log.e(TAG, "decodeBase64 failed: " + e.getMessage());
            return null;
        }
    }

    private SharedPreferences prefs() {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, CHANNEL_NM, NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Alerts when your territory is stolen");
            ch.enableVibration(true);
            ch.setVibrationPattern(new long[]{0, 300, 150, 300});
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }
}
