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

Since this uses Sketchware Pro (no Gradle), setup is manual:

1. **Create a new project** in Sketchware Pro
   - Package: `com.rohit.turfwar`
   - Min SDK: 21

2. **WebView setup**
   - Add a WebView component filling the full screen
   - Enable JavaScript, DOM storage, geolocation in WebView settings
   - Load `file:///android_asset/index.html`

3. **Add custom Java classes**
   - Copy `TurfWarBridge.java`, `RunTrackingService.java`, `LocationCheckService.java`, `AlarmReceiver.java` into the custom classes section

4. **Add assets**
   - Place `index.html` in the `assets/` folder

5. **AndroidManifest additions**
   - Add permissions from `AndroidManifest_additions.xml`:
     - `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`
     - `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `FOREGROUND_SERVICE_HEALTH`
     - `RECEIVE_BOOT_COMPLETED`, `VIBRATE`, `INTERNET`
     - `POST_NOTIFICATIONS` (Android 13+)
   - Register services and receiver

6. **Firebase**
   - Create a Firebase project
   - Enable Firestore + Authentication (Email/Password + Google)
   - Copy your config into `index.html` where `FB_CFG` is defined

7. **FileProvider**
   - Add `file_paths.xml` to `res/xml/`
   - Register provider in manifest for image sharing

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
