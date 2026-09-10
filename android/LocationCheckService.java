package com.rohit.turfwar;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * LocationCheckService
 *
 * Runs as a short-lived ForegroundService whenever AlarmReceiver fires.
 * Queries Firestore REST API for unnotified steal events against this device,
 * fires a system notification for each new steal, then stops itself.
 *
 * No Android SDK dependencies (Gradle-free) — uses plain HttpURLConnection.
 *
 * Firestore rules must allow: read steals where stolenFrom == deviceId
 *   allow read: if true;  (or tighter rule with auth)
 */
public class LocationCheckService extends Service {

    private static final String TAG         = "TurfWarSvc";
    private static final String CHANNEL_ID  = TurfWarBridge.CHANNEL_ID;
    private static final String PREFS       = TurfWarBridge.PREFS;
    private static final int    FG_NOTIF_ID = 9001;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");
        startForegroundCompat();
        new Thread(() -> {
            checkForSteals();
            stopSelf();
        }).start();
        return START_NOT_STICKY;
    }

    /* ================================================================
       START FOREGROUND — handles API 8 through 15
    ================================================================ */
    private void startForegroundCompat() {
        ensureChannel();

        Intent tapIntent = new Intent(this, MainActivity.class);
        tapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        int piFlags = Build.VERSION.SDK_INT >= 23
                ? PendingIntent.FLAG_IMMUTABLE
                : 0;
        PendingIntent pi = PendingIntent.getActivity(this, 0, tapIntent, piFlags);

        Notification notif = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("TurfWar")
                .setContentText("Checking your territories...")
                .setSmallIcon(android.R.drawable.ic_menu_mapmode)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {         // API 29+
                // DATA_SYNC doesn't need the location permission
                startForeground(FG_NOTIF_ID, notif,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(FG_NOTIF_ID, notif);                       // API 8–28
            }
        } catch (SecurityException e) {
            // Fallback if permission not granted yet
            Log.w(TAG, "startForeground security exception: " + e.getMessage());
            try { startForeground(FG_NOTIF_ID, notif); } catch (Exception ex) { Log.e(TAG, "startForeground failed", ex); }
        }
    }

    /* ================================================================
       CHECK FIRESTORE FOR UNNOTIFIED STEALS (REST API — no Gradle)
    ================================================================ */
    private void checkForSteals() {
        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String deviceId   = prefs.getString("deviceId", "");
        String projectId  = prefs.getString("projectId", "");

        if (deviceId.isEmpty() || projectId.isEmpty()) {
            Log.w(TAG, "deviceId or projectId not set — skipping check");
            return;
        }

        // IDs of steals we have already shown a notification for
        Set<String> notified = new HashSet<>(
                prefs.getStringSet("notifiedSteals", new HashSet<>()));

        try {
            /*
             * Firestore runQuery — structured query:
             * SELECT * FROM steals
             *   WHERE stolenFrom == deviceId
             *   AND   notified   == false
             *   ORDER BY timestamp DESC
             *   LIMIT 10
             *
             * Note: two equality filters on different fields DO require
             * a composite index (stolenFrom ASC, notified ASC).
             * If you haven't created it yet, simplify to just filter
             * by stolenFrom here and check notified in Java below.
             */
            String endpoint = "https://firestore.googleapis.com/v1/projects/"
                    + projectId
                    + "/databases/(default)/documents:runQuery";

            // Query: filter only by stolenFrom (single equality — no composite index)
            // notified check done in Java to avoid index requirement
            String queryJson = "{"
                + "\"structuredQuery\":{"
                +   "\"from\":[{\"collectionId\":\"steals\"}],"
                +   "\"where\":{\"fieldFilter\":{"
                +     "\"field\":{\"fieldPath\":\"stolenFrom\"},"
                +     "\"op\":\"EQUAL\","
                +     "\"value\":{\"stringValue\":\"" + escapeJson(deviceId) + "\"}"
                +   "}},"
                +   "\"orderBy\":[{\"field\":{\"fieldPath\":\"timestamp\"},\"direction\":\"DESCENDING\"}],"
                +   "\"limit\":20"
                + "}}";

            String response = httpPost(endpoint, queryJson);
            if (response == null) { Log.w(TAG, "No response from Firestore"); return; }

            JSONArray results = new JSONArray(response);

            for (int i = 0; i < results.length(); i++) {
                JSONObject item = results.getJSONObject(i);
                if (!item.has("document")) continue;

                JSONObject doc    = item.getJSONObject("document");
                String docName    = doc.getString("name");
                String stealId    = docName.substring(docName.lastIndexOf('/') + 1);
                JSONObject fields = doc.getJSONObject("fields");

                // Java-side notified filter (avoids composite index)
                boolean alreadyNotified = false;
                if (fields.has("notified")) {
                    JSONObject nf = fields.getJSONObject("notified");
                    alreadyNotified = nf.optBoolean("booleanValue", false);
                }
                if (alreadyNotified || notified.contains(stealId)) continue;

                // Parse steal details
                String stolenByName = getStringField(fields, "stolenByName");
                int    area         = getIntField(fields, "area");

                // Fire notification
                fireStealNotification(stolenByName, area);
                notified.add(stealId);

                // Mark notified in Firestore (fire-and-forget on background thread)
                final String sid = stealId;
                new Thread(() -> markNotified(projectId, sid)).start();
            }

            // Persist updated notified set (trim if too large)
            if (notified.size() > 300) {
                // Keep newest 150 — sets have no guaranteed order, so just trim
                Set<String> trimmed = new HashSet<>();
                int count = 0;
                for (String id : notified) { if (count++ < 150) trimmed.add(id); }
                notified = trimmed;
            }
            prefs.edit().putStringSet("notifiedSteals", notified).apply();

        } catch (Exception e) {
            Log.e(TAG, "checkForSteals error: " + e.getMessage());
        }
    }

    /* ================================================================
       MARK STEAL AS NOTIFIED via Firestore PATCH
    ================================================================ */
    private void markNotified(String projectId, String stealId) {
        String endpoint = "https://firestore.googleapis.com/v1/projects/"
                + projectId
                + "/databases/(default)/documents/steals/"
                + stealId
                + "?updateMask.fieldPaths=notified";

        String body = "{\"fields\":{\"notified\":{\"booleanValue\":true}}}";

        try {
            URL url = new URL(endpoint);
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setRequestMethod("PATCH");
            c.setRequestProperty("Content-Type", "application/json");
            c.setDoOutput(true);
            c.setConnectTimeout(10_000);
            c.setReadTimeout(10_000);
            try (OutputStream os = c.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
            c.getResponseCode(); // execute
            c.disconnect();
        } catch (Exception e) {
            Log.w(TAG, "markNotified error: " + e.getMessage());
        }
    }

    /* ================================================================
       SHOW STEAL NOTIFICATION
    ================================================================ */
    private void fireStealNotification(String stolenByName, int area) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        int piFlags = Build.VERSION.SDK_INT >= 23
                ? PendingIntent.FLAG_IMMUTABLE
                : 0;
        PendingIntent pi = PendingIntent.getActivity(
                this, (int)(System.currentTimeMillis() % 100000), intent, piFlags);

        String body = stolenByName + " grabbed " + area + "m² of your turf!";

        NotificationCompat.Builder nb = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Territory Stolen! 🚨")
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setVibrate(new long[]{0, 300, 150, 300});

        NotificationManager nm = (NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify((int)(System.currentTimeMillis() % 100000), nb.build());
    }

    /* ================================================================
       HTTP HELPERS
    ================================================================ */
    private String httpPost(String urlStr, String body) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/json");
            c.setDoOutput(true);
            c.setConnectTimeout(15_000);
            c.setReadTimeout(15_000);

            try (OutputStream os = c.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int code = c.getResponseCode();
            if (code != 200) {
                Log.w(TAG, "HTTP " + code + " from Firestore");
                c.disconnect();
                return null;
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
            c.disconnect();
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "httpPost error: " + e.getMessage());
            return null;
        }
    }

    /* ================================================================
       JSON FIELD HELPERS (no Gson/Jackson — no Gradle required)
    ================================================================ */
    private String getStringField(JSONObject fields, String key) {
        try { return fields.getJSONObject(key).getString("stringValue"); }
        catch(Exception e) { return ""; }
    }

    private int getIntField(JSONObject fields, String key) {
        try {
            JSONObject f = fields.getJSONObject(key);
            if (f.has("integerValue")) return f.getInt("integerValue");
            if (f.has("doubleValue"))  return (int) f.getDouble("doubleValue");
            return 0;
        } catch(Exception e) { return 0; }
    }

    private String escapeJson(String s) {
        return s.replace("\\","\\\\").replace("\"","\\\"");
    }

    /* ================================================================
       NOTIFICATION CHANNEL (needed on API 26+)
    ================================================================ */
    private void ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, TurfWarBridge.CHANNEL_NM,
                    NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("TurfWar territory steal alerts");
            ch.enableVibration(true);
            ch.setVibrationPattern(new long[]{0,300,150,300});
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
