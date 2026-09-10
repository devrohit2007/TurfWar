// =============================================================
//  TURFWAR — MainActivity onCreate additions
//  Sketchware Pro with View Binding
//
//  Paste inside onCreate(), after super.onCreate() and
//  setContentView(). Your WebView is accessed as:
//      binding.webview1
// =============================================================


// ── 1. WebView Settings ───────────────────────────────────────

android.webkit.WebSettings ws = binding.webview1.getSettings();
ws.setJavaScriptEnabled(true);
ws.setDomStorageEnabled(true);
ws.setGeolocationEnabled(true);
ws.setDatabaseEnabled(true);
ws.setAllowFileAccess(true);
ws.setAllowFileAccessFromFileURLs(true);
ws.setAllowUniversalAccessFromFileURLs(true);
ws.setCacheMode(android.webkit.WebSettings.LOAD_DEFAULT);
ws.setMediaPlaybackRequiresUserGesture(false);


// ── 1b. User-Agent override — REQUIRED for Google Sign-In ─────
// Google refuses to show its login page inside a WebView and
// returns "disallowed_useragent" — it detects this by checking
// for ";wv)" in the user agent string. Stripping it makes the
// request look like it's coming from Chrome directly, which
// Google allows.
String uaOriginal = ws.getUserAgentString();
String uaFixed = uaOriginal.replace("; wv", "");
ws.setUserAgentString(uaFixed);


// ── 1c. Third-party cookies — REQUIRED for Firebase redirect ──
// Firebase's signInWithRedirect() flow sets a cookie on Google's
// domain mid-flow and reads it back on return. Without this,
// login silently fails after the redirect completes.
android.webkit.CookieManager cookieManager = android.webkit.CookieManager.getInstance();
cookieManager.setAcceptCookie(true);
cookieManager.setAcceptThirdPartyCookies(binding.webview1, true);


// ── 2. Attach Java Bridge (JS calls Android.method()) ─────────
binding.webview1.addJavascriptInterface(new TurfWarBridge(this), "Android");


// ── 3. WebChromeClient — GPS permission + console logs ─────────
binding.webview1.setWebChromeClient(new android.webkit.WebChromeClient() {

    @Override
    public void onGeolocationPermissionsShowPrompt(
            String origin,
            android.webkit.GeolocationPermissions.Callback callback) {
        // Auto-grant GPS permission to our own app (file:// origin)
        callback.invoke(origin, true, false);
    }

    @Override
    public boolean onConsoleMessage(android.webkit.ConsoleMessage msg) {
        android.util.Log.d("TurfWarJS",
            msg.message() + " [" + msg.sourceId() + ":" + msg.lineNumber() + "]");
        return true;
    }
});


// ── 4. WebViewClient — keep navigation inside WebView ──────────
// Returning false lets Google's OAuth pages load inline too,
// which is what we want — no external browser needed.
binding.webview1.setWebViewClient(new android.webkit.WebViewClient() {

    @Override
    public boolean shouldOverrideUrlLoading(
            android.webkit.WebView view, String url) {
        return false;
    }

    @Override
    public void onReceivedError(
            android.webkit.WebView view, int errorCode,
            String description, String failingUrl) {
        android.util.Log.e("TurfWar",
            "WebView error " + errorCode + ": " + description);
    }
});


// ── 5. Runtime Permissions (Android 6+) ─────────────────────────
if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {

    java.util.List<String> permsNeeded = new java.util.ArrayList<>();

    if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        permsNeeded.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
        permsNeeded.add(android.Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    // POST_NOTIFICATIONS required on Android 13+
    if (android.os.Build.VERSION.SDK_INT >= 33) {
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permsNeeded.add(android.Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    // WRITE_EXTERNAL_STORAGE only needed on Android 9 and below
    // to save share-card images (Android 10+ uses MediaStore,
    // which needs no permission at all).
    if (android.os.Build.VERSION.SDK_INT < 29) {
        if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permsNeeded.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
    }

    if (!permsNeeded.isEmpty()) {
        requestPermissions(permsNeeded.toArray(new String[0]), 100);
    }
}


// ── 6. Load the app ─────────────────────────────────────────────
binding.webview1.loadUrl("file:///android_asset/index.html");


// =============================================================
//  ALSO ADD THIS METHOD to MainActivity class (outside onCreate):
//  Handles permission dialog results
// =============================================================
/*
@Override
public void onRequestPermissionsResult(int requestCode,
        String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == 100) {
        boolean locGranted = false;
        for (int i = 0; i < permissions.length; i++) {
            if (android.Manifest.permission.ACCESS_FINE_LOCATION.equals(permissions[i])
                    && grantResults[i] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                locGranted = true;
            }
        }
        if (locGranted) {
            binding.webview1.reload();
        }
    }
}
*/


// =============================================================
//  ALSO ADD THIS METHOD to MainActivity class (outside onCreate):
//  Handles Android back button — go back in WebView history
//  first (e.g. out of Google sign-in) instead of closing the app
// =============================================================
/*
@Override
public void onBackPressed() {
    if (binding.webview1.canGoBack()) {
        binding.webview1.goBack();
    } else {
        super.onBackPressed();
    }
}
*/
