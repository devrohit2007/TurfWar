# 🗺️ TurfWar — Claim Your Territory

> A real-time territory-claiming running app built entirely on a phone using Sketchware Pro — no PC, no Android Studio.

---

## 📖 What is TurfWar?

TurfWar is an Android app that turns your city into a game. You go outside, run or walk a loop around an area, and when you close the loop — that territory is **yours**. Other players can steal it by running the same area. The leaderboard ranks players by total territory owned (in m²).

Think Pokémon GO meets Strava — but focused purely on claiming and defending real-world zones.

---

## 🎯 Why We Built This

This started as a personal challenge: **can a student with only a phone build a full production-quality Android app?**

No laptop. No Android Studio. No Gradle. Just:
- **Sketchware Pro** — visual Android IDE on phone
- **Termux** — terminal for Git and file management
- **Claude AI** — senior developer / debugging partner

The goal was to prove that mobile-first development is real, and to ship something people would actually want to use outdoors.

---

## ✨ Features

### 🏃 Running & Territory
- **GPS loop tracking** — walk/run a closed loop to claim the enclosed area
- **Normal run mode** — track distance, time, pace without claiming
- **Live run stats** — KM, time, pace, m² updated in real-time on map
- **Warmup timer** — 30-second GPS lock countdown before run starts
- **Run share card** — generate and save/share a summary image after each run

### 🗺️ Map
- **OpenStreetMap** via Leaflet.js — fully offline-capable tile rendering
- **Dark map theme** — custom dark tiles matching app theme
- **Territory polygons** — all claimed zones rendered as colored overlays
- **Live GPS dot** — your position tracked in real-time during runs
- **Back button** — navigate back to home without losing run state

### 📊 Stats & Leaderboard
- **Home dashboard** — Total Territory, Zones Owned, KM Covered, Kcal Burned, Global Rank
- **Stats screen** — full history grid with run records
- **Live leaderboard** — real-time Firestore listener, highlights your position
- **Run history** — every run logged with date, distance, time, pace

### 🔔 Notifications
- **Live run timer** — persistent foreground notification with OS-native chronometer (smooth tick, no flicker)
- **Steal alerts** — push notification when someone takes your territory (background check via AlarmManager)
- **Background service** — `RunTrackingService` keeps timer alive even when WebView is throttled

### 👤 Auth & Profile
- **Firebase Auth** — email/password + Google sign-in
- **Email verification** — full verify screen with step guide and success animation
- **Profile settings** — display name, avatar color picker
- **Sign-out confirmation** — professional in-app modal (no ugly browser dialog)

### 🎨 Design
- **Dark green theme** — custom TurfWar palette, Strava/NRC-inspired
- **Light mode** — full light theme with all components correctly overridden
- **Glass morphism cards** — blur + transparency throughout
- **Smooth transitions** — splash animation, screen transitions, toast notifications

---

## 🏗️ Architecture

```
TurfWar Android App
├── WebView (full-screen)
│   └── index.html  ← entire UI + app logic (Vanilla JS)
│       ├── Firebase SDK (auth + firestore)
│       ├── Leaflet.js (map)
│       └── Turf.js (GPS polygon math)
│
├── TurfWarBridge.java  ← JS ↔ Android bridge (@JavascriptInterface)
│   ├── saveImageToGallery()
│   ├── shareImage()
│   ├── showNotification() / showStealNotification()
│   ├── startRunService() / stopRunService()
│   ├── vibrateDevice()
│   └── startBackgroundChecks() / stopBackgroundChecks()
│
├── RunTrackingService.java  ← Foreground service for live run notification
│   ├── OS-native chronometer (smooth timer, no nm.notify() every second)
│   ├── ACTION_START / ACTION_UPDATE / ACTION_STOP intents
│   └── Handles pause state, pace calculation
│
├── LocationCheckService.java  ← Background steal checker
│   ├── Triggered by AlarmManager every 15 minutes
│   ├── Queries Firestore REST API (no Gradle SDK needed)
│   └── Fires steal notification with fixed ID (no stacking)
│
└── AlarmReceiver.java  ← BroadcastReceiver for alarm + BOOT_COMPLETED
```

---

## 🔧 Tech Stack

| Layer | Technology |
|-------|-----------|
| Android shell | Sketchware Pro (Java, no Gradle) |
| UI & logic | Vanilla HTML/CSS/JS (single file) |
| Map | Leaflet.js + OpenStreetMap |
| Geo math | Turf.js (polygon area, point-in-polygon) |
| Backend | Firebase Firestore (real-time) |
| Auth | Firebase Authentication |
| Background | AlarmManager + ForegroundService |
| Dev environment | Sketchware Pro + Termux (phone only) |
| Version control | Git + GitHub via Termux |

---

## 📁 File Structure

```
turfwar/
├── index.html                    # Full app — UI, logic, Firebase, Leaflet
├── TurfWarBridge.java            # Android ↔ JS bridge
├── RunTrackingService.java       # Live run foreground service
├── LocationCheckService.java     # Background steal check service
├── AlarmReceiver.java            # Alarm + boot receiver
├── file_paths.xml                # FileProvider config (image sharing)
├── AndroidManifest_additions.xml # Permissions + service declarations
└── README.md
```

---

## 🚀 How to Build (Sketchware Pro)

> This project was built entirely on a phone using **Sketchware Pro**. No PC, no Android Studio, no Gradle.

### Step 1 — Create New Project

- Open Sketchware Pro → **New Project**
- **App Name:** TurfWar
- **Package:** `com.rohit.turfwar`
- **Min SDK:** 21 (Android 5.0)

---

### Step 2 — Setup the WebView

In your `MainActivity` layout:
- Add a **WebView** component that fills the full screen (width: `match_parent`, height: `match_parent`)
- Give it the variable name `webview1`

In your `MainActivity` **onCreate** block, add this Java code:

```java
webview1.getSettings().setJavaScriptEnabled(true);
webview1.getSettings().setDomStorageEnabled(true);
webview1.getSettings().setGeolocationEnabled(true);
webview1.getSettings().setAllowFileAccessFromFileURLs(true);
webview1.getSettings().setAllowUniversalAccessFromFileURLs(true);
webview1.getSettings().setMediaPlaybackRequiresUserGesture(false);
webview1.setWebChromeClient(new android.webkit.WebChromeClient() {
    @Override
    public void onGeolocationPermissionsShowPrompt(String origin, android.webkit.GeolocationPermissions.Callback callback) {
        callback.invoke(origin, true, false);
    }
});
webview1.addJavascriptInterface(new TurfWarBridge(this), "Android");
webview1.loadUrl("file:///android_asset/index.html");
```

---

### Step 3 — Add index.html as Asset

- In Sketchware Pro → your project → **Files** tab
- Go to **Assets** folder
- Import `index.html` from storage
- The file will be accessible at `file:///android_asset/index.html`

---

### Step 4 — Add Custom Java Classes

In Sketchware Pro → your project → **Library** tab → **Custom classes**:

Add each file one by one:
- `TurfWarBridge.java`
- `RunTrackingService.java`
- `LocationCheckService.java`
- `AlarmReceiver.java`

Paste the full file contents into each custom class entry.

---

### Step 5 — AndroidManifest Permissions

In Sketchware Pro → **AndroidManifest** section, add these permissions:

```xml
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"/>
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_HEALTH"/>
<uses-permission android:name="android.permission.VIBRATE"/>
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28"/>
```

And inside the `<application>` tag, register services and provider:

```xml
<service android:name=".RunTrackingService"
    android:foregroundServiceType="location|health"
    android:exported="false"/>

<service android:name=".LocationCheckService"
    android:foregroundServiceType="dataSync"
    android:exported="false"/>

<receiver android:name=".AlarmReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED"/>
    </intent-filter>
</receiver>

<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths"/>
</provider>
```

Full manifest additions are in `android/AndroidManifest_additions.xml`.

---

### Step 6 — Firebase Setup

1. Go to [console.firebase.google.com](https://console.firebase.google.com)
2. Create a new project
3. Add an Android app with package `com.rohit.turfwar`
4. Enable **Firestore Database** (start in test mode)
5. Enable **Authentication** → Email/Password + Google
6. Copy your Firebase config and paste it into `index.html` where you see `FB_CFG`:

```javascript
const FB_CFG = {
  apiKey: "YOUR_API_KEY",
  authDomain: "YOUR_PROJECT.firebaseapp.com",
  projectId: "YOUR_PROJECT_ID",
  storageBucket: "YOUR_PROJECT.appspot.com",
  messagingSenderId: "YOUR_SENDER_ID",
  appId: "YOUR_APP_ID"
};
```

---

### Step 7 — FileProvider XML

In Sketchware Pro → **Files** → `res/xml/` → create `file_paths.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="images" path="images/"/>
    <external-path name="external" path="."/>
</paths>
```

---

### Step 8 — Request Runtime Permissions

In your `MainActivity` onCreate, request location and notification permissions:

```java
if (Build.VERSION.SDK_INT >= 23) {
    requestPermissions(new String[]{
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION
    }, 1);
}
if (Build.VERSION.SDK_INT >= 33) {
    requestPermissions(new String[]{
        android.Manifest.permission.POST_NOTIFICATIONS
    }, 2);
}
```

---

### Step 9 — Build & Install

- In Sketchware Pro → **Build** → Export APK
- Install on your device
- Grant location permissions when prompted

---

## 🔥 Firebase Firestore Structure

```
users/{uid}
  name: string
  color: string
  totalArea: number
  territoriesCount: number
  totalDistance: number
  totalCalories: number

territories/{docId}
  owner: string
  uid: string
  color: string
  coordinates: array
  area: number
  deviceId: string
  timestamp: timestamp

steals/{docId}
  stolenFrom: string (deviceId)
  stolenBy: string (uid)
  stolenByName: string
  area: number
  timestamp: timestamp
  notified: boolean

runs/{docId}
  uid: string
  distance: number
  duration: number
  calories: number
  timestamp: timestamp
```

---

## 🐛 Key Bugs Fixed During Development

Building without a PC meant debugging everything live on device. Major fixes included:

- **Splash screen hang** — `boot().then(clearTimeout)` was killing the safety net before Firebase resolved
- **Notification stacking** — moved to `RunTrackingService` with fixed notification ID and OS-native chronometer
- **GPS first point dropped** — path only pushed inside `if(last)` block, missing coordinate 1
- **Canvas letterSpacing** — not a valid Canvas 2D API property (CSS-only), was silently failing
- **Firestore integer parsing** — REST API returns `integerValue` as a string `"42"`, not a number
- **WebView flex layout** — navbar floated mid-screen; fixed with `position:fixed;bottom:0`
- **syncQueue stale UID** — offline territories synced under wrong account after re-login
- **`@JavascriptInterface` overloads** — Android bridge doesn't support overloaded method names

---

## 👨‍💻 Built By

**Rohit** — independent Android developer, built entirely on a phone.

- Tools: Sketchware Pro, Termux, Claude AI
- No PC. No Android Studio. No Gradle.
- Delhi, India 🇮🇳

---

## 📄 License

MIT License — feel free to learn from it, fork it, build on it.

---

*Built phone-first. Shipped from Termux. Debugged in the wild.* 🏃
