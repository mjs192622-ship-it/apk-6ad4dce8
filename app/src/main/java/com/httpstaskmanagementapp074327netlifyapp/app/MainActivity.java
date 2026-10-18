package com.httpstaskmanagementapp074327netlifyapp.app;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.View;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.widget.ProgressBar;
import android.app.DownloadManager;
import android.net.Uri;
import android.os.Environment;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.widget.Toast;
import android.Manifest;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.os.Build;
import android.widget.Toast;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.ServiceWorkerClient;
import android.webkit.ServiceWorkerController;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import android.view.Menu;
import android.view.MenuItem;
import android.app.AlertDialog;
import android.net.Uri;
import android.webkit.ValueCallback;
import android.provider.MediaStore;
import android.os.Environment;
import android.content.ContentValues;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends androidx.appcompat.app.AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;
    private String fcmTokenForWebView = "";
    private static final String WEBSITE_URL = "https://task-management-app-074327.netlify.app";
    private SwipeRefreshLayout swipeRefresh;
    private ValueCallback<Uri[]> fileUploadCallback;
    private Uri cameraImageUri;
    private long backPressedTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Immersive sticky mode - hide navigation bar
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        // Set system bar colors
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int systemBarColor = Color.parseColor("#0000FF");
            getWindow().setStatusBarColor(systemBarColor);
            getWindow().setNavigationBarColor(systemBarColor);
        }
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setColorSchemeColors(Color.parseColor("#6366F1"));
        swipeRefresh.setOnRefreshListener(() -> {
            webView.reload();
        });
        checkAndShowRateDialog(5);
        
        setupWebView();
        
        // Enable Service Worker support (API 24+)
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            ServiceWorkerController swController = ServiceWorkerController.getInstance();
            swController.setServiceWorkerClient(new ServiceWorkerClient() {
                @Override
                public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
                    return null;
                }
            });
        }

        // Request POST_NOTIFICATIONS permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
            }
        }

        // Explicitly fetch and send FCM token at app startup
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        final String fcmToken = task.getResult();
                        fcmTokenForWebView = fcmToken;
                        android.util.Log.d("FCM_TOKEN", "Token received: " + fcmToken);

                        // Show token status via JavaScript in WebView (debug)
                        runOnUiThread(() -> {
                            if (webView != null) {
                                webView.evaluateJavascript(
                                    "window.FCM_TOKEN = '" + fcmToken + "'; window.dispatchEvent(new Event('fcm_token_ready')); console.log('FCM Token set');", null);
                            }
                        });

                        new Thread(() -> {
                            try {
                                // Endpoint is generated by PHP based on Website URL:
                                // User app  => /fcm_token.php
                                // Admin app => /admin/fcm_token.php
                                String endpoint = "https://task-management-app-074327.netlify.app/fcm_token.php";
                                android.util.Log.d("FCM_TOKEN", "Sending token to: " + endpoint);

                                java.net.URL tokenUrl = new java.net.URL(endpoint);
                                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) tokenUrl.openConnection();
                                conn.setRequestMethod("POST");
                                conn.setRequestProperty("Content-Type", "application/json");
                                conn.setRequestProperty("User-Agent", "BPWalletApp/1.0");
                                conn.setDoOutput(true);
                                conn.setConnectTimeout(15000);
                                conn.setReadTimeout(15000);
                                String body = "{\"token\":\"" + fcmToken + "\"}";
                                try (java.io.OutputStream os = conn.getOutputStream()) {
                                    os.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                                }
                                int status = conn.getResponseCode();
                                android.util.Log.d("FCM_TOKEN", "POST response: HTTP " + status);
                                conn.disconnect();

                                // Fallback: GET request if POST fails
                                if (status < 200 || status >= 300) {
                                    android.util.Log.d("FCM_TOKEN", "POST failed, trying GET fallback...");
                                    String t = java.net.URLEncoder.encode(fcmToken, "UTF-8");
                                    java.net.URL fallbackUrl = new java.net.URL(endpoint + "?token=" + t);
                                    java.net.HttpURLConnection fallbackConn = (java.net.HttpURLConnection) fallbackUrl.openConnection();
                                    fallbackConn.setRequestMethod("GET");
                                    fallbackConn.setRequestProperty("User-Agent", "BPWalletApp/1.0");
                                    fallbackConn.setConnectTimeout(15000);
                                    fallbackConn.setReadTimeout(15000);
                                    int fbStatus = fallbackConn.getResponseCode();
                                    android.util.Log.d("FCM_TOKEN", "GET fallback response: HTTP " + fbStatus);
                                    fallbackConn.disconnect();
                                }
                            } catch (Exception e) {
                                android.util.Log.e("FCM_TOKEN", "Error sending token: " + e.getMessage(), e);
                            }
                        }).start();
                    } else {
                        android.util.Log.e("FCM_TOKEN", "Failed to get token: " + (task.getException() != null ? task.getException().getMessage() : "unknown error"));
                        // Show error as Toast so user can see
                        runOnUiThread(() -> {
                            String errMsg = task.getException() != null ? task.getException().getMessage() : "Unknown FCM error";
                            Toast.makeText(MainActivity.this, "FCM Error: " + errMsg, Toast.LENGTH_LONG).show();
                        });
                    }
                });
        } catch (Exception e) {
            android.util.Log.e("FCM_TOKEN", "Firebase init error: " + e.getMessage(), e);
            Toast.makeText(this, "Firebase Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        // Schedule background notification polling every 15 minutes
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
            NotificationWorker.class, 15, TimeUnit.MINUTES)
            .build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "notification_check",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest);
        
        // Request runtime permissions
        java.util.List<String> permissionsNeeded = new java.util.ArrayList<>();
        String[] requiredPerms = new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};
        for (String perm : requiredPerms) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(perm);
            }
        }
        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), 2001);
        }
        
        // Add JavaScript Bridge
        webView.addJavascriptInterface(new NativeBridge(), "AndroidBridge");
        setupBottomNav();
        checkForUpdate();
        
        // Handle deep link intent
        handleIntent(getIntent());
        
        // Load directly; ConnectivityManager can be unreliable on some devices/VPNs.
        // WebView will show its own error page if the connection is actually unavailable.
        webView.loadUrl(WEBSITE_URL);
    }

    private void checkAndShowRateDialog(int targetLaunches) {
        android.content.SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        boolean ratedAlready = prefs.getBoolean("rated", false);
        if (ratedAlready) return;
        int launches = prefs.getInt("launch_count", 0) + 1;
        prefs.edit().putInt("launch_count", launches).apply();
        if (launches == targetLaunches) {
            new AlertDialog.Builder(this)
                .setTitle("Enjoying the app?")
                .setMessage("If you like the app, please take a moment to rate it. It won't take more than a minute. Thanks!")
                .setPositiveButton("Rate Now", (d, w) -> {
                    prefs.edit().putBoolean("rated", true).apply();
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + getPackageName())));
                    } catch (android.content.ActivityNotFoundException e) {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + getPackageName())));
                    }
                })
                .setNeutralButton("Remind Later", null)
                .setNegativeButton("No Thanks", (d, w) -> prefs.edit().putBoolean("rated", true).apply())
                .show();
        }
    }

    private void setupWebView() {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setSupportZoom(true);
        webSettings.setDefaultTextEncodingName("utf-8");
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setLoadsImagesAutomatically(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setDatabaseEnabled(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        android.webkit.CookieManager.getInstance().setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                // Save cookies for background notification worker
                String cookies = CookieManager.getInstance().getCookie(WEBSITE_URL);
                if (cookies != null && !cookies.isEmpty()) {
                    getSharedPreferences("bp_prefs", MODE_PRIVATE)
                        .edit()
                        .putString("session_cookies", cookies)
                        .putString("website_url", WEBSITE_URL)
                        .apply();
                }
                // Re-inject FCM token after every page load so website JS can POST it with user_id/session.
                if (fcmTokenForWebView != null && !fcmTokenForWebView.isEmpty()) {
                    view.evaluateJavascript("window.FCM_TOKEN = '" + fcmTokenForWebView + "'; window.dispatchEvent(new Event('fcm_token_ready'));", null);
                }
                // Custom JavaScript injection
                view.evaluateJavascript("let currentTaskIndex = null; let currentEnergy = \'medium\'; let userName = localStorage.getItem(\'userNameBadrApp\') || \'\';  let tasks = JSON.parse(localStorage.getItem(\'myCleanTasksBadr\')) || [];  if (userName) {     showHome(); } else {     document.getElementById(\'welcomeScreen\').classList.remove(\'hidden\');     document.getElementById(\'homeScreen\').classList.add(\'hidden\'); }  function saveUserName() {     const input = document.getElementById(\'userNameInput\');     if (!input.value.trim()) {         alert(\"يرجى كتابة اسمك أولاً!\");         return;     }     userName = input.value.trim();     localStorage.setItem(\'userNameBadrApp\', userName);     showHome(); }  function resetUser() {     localStorage.removeItem(\'userNameBadrApp\');     location.reload(); }  function showHome() {     document.getElementById(\'welcomeScreen\').classList.add(\'hidden\');     document.getElementById(\'homeScreen\').classList.remove(\'hidden\');     document.getElementById(\'displayName\').innerText = userName;     setEnergy(\'medium\'); }  function setEnergy(level) {     currentEnergy = level;          document.querySelectorAll(\'.energy-btn\').forEach(btn => btn.classList.remove(\'active\'));     document.querySelector(`.energy-btn.${level}`).classList.add(\'active\');      const message = document.getElementById(\'animeMessage\');          if (level === \'low\') {         message.innerText = `أهلاً يا ${userName}! طاقتك قليلة؟ رتبت لك المهام (السهلة أولاً) لتنجزها براحة! 🍃`;     } else if (level === \'medium\') {         message.innerText = `أهلاً بك يا ${userName}! رائع، وضعنا المهام المتوسطة في البداية! ⚡`;     } else {         message.innerText = `ما شاء الله يا ${userName}! طاقة مشتعلة! رتبت لك المهام الصعبة في المقدمة! 🔥`;     }      renderTasks(); }  function openAddScreen() {     document.getElementById(\'homeScreen\').classList.add(\'hidden\');     document.getElementById(\'addScreen\').classList.remove(\'hidden\'); }  function closeAddScreen() {     document.getElementById(\'addScreen\').classList.add(\'hidden\');     document.getElementById(\'homeScreen\').classList.remove(\'hidden\'); }  function addTask() {     const input = document.getElementById(\'taskInput\');     const diffRadio = document.querySelector(\'input[name=\"taskDifficulty\"]:checked\');          if (!input.value.trim()) return;      const difficulty = diffRadio ? diffRadio.value : \'medium\';      tasks.push({          text: input.value.trim(),          done: false,          image: null,          difficulty: difficulty      });          input.value = \'\';     saveAndRender();     closeAddScreen(); }  function renderTasks() {     const list = document.getElementById(\'taskList\');     list.innerHTML = \'\';      if (tasks.length === 0) {         list.innerHTML = `<p style=\"text-align: center; color: #777; font-size: 13px; padding: 20px;\">لا توجد مهام حالياً. اضغط على \"إضافة مهمة جديدة\" لإضافتها!</p>`;     }      let sortedTasks = [...tasks];          if (currentEnergy === \'high\') {         sortedTasks.sort((a, b) => getDiffScore(b.difficulty) - getDiffScore(a.difficulty));     } else if (currentEnergy === \'low\') {         sortedTasks.sort((a, b) => getDiffScore(a.difficulty) - getDiffScore(b.difficulty));     }      let completedCount = 0;      sortedTasks.forEach((task) => {         if (task.done) completedCount++;          const originalIndex = tasks.findIndex(t => t.text === task.text);          const li = document.createElement(\'li\');         li.classList.add(`diff-${task.difficulty}`);         if (task.done) li.classList.add(\'done\');          let imageHTML = task.image ? `<img src=\"${task.image}\" class=\"proof-img\" alt=\"إثبات الإنجاز\">` : \'\';         let diffText = task.difficulty === \'high\' ? \'🔴 صعبة\' : task.difficulty === \'medium\' ? \'🟡 متوسطة\' : \'🟢 سهلة\';          li.innerHTML = `             <div class=\"task-header\">                 <div>                     <span class=\"task-title\" style=\"font-weight: bold;\">${task.text}</span>                     <span style=\"font-size: 11px; margin-right: 8px; color: #666;\">(${diffText})</span>                 </div>                 ${task.done                      ? \'<span style=\"color: #2e7d32; font-weight: bold; font-size: 12px;\">✅ تم الإنجاز</span>\'                      : `<button class=\"verify-btn\" onclick=\"openCamera(${originalIndex})\">إثبات بالصورة 📸</button>`                 }             </div>             ${imageHTML}         `;         list.appendChild(li);     });      document.getElementById(\'completedCount\').innerText = `${completedCount} / ${tasks.length}`;     const progressPercent = tasks.length > 0 ? (completedCount / tasks.length) * 100 : 0;     document.getElementById(\'progressFill\').style.width = `${progressPercent}%`; }  function getDiffScore(diff) {     if (diff === \'low\') return 1;     if (diff === \'medium\') return 2;     if (diff === \'high\') return 3;     return 2; }  function openCamera(index) {     currentTaskIndex = index;     document.getElementById(\'cameraModal\').classList.remove(\'hidden\');          navigator.mediaDevices.getUserMedia({ video: { facingMode: \"environment\" } })         .then(stream => {             document.getElementById(\'video\').srcObject = stream;         })         .catch(err => alert(\"يرجى السماح بالوصول للكاميرا\")); }  function closeCamera() {     const video = document.getElementById(\'video\');     if (video.srcObject) {         video.srcObject.getTracks().forEach(track => track.stop());     }     document.getElementById(\'cameraModal\').classList.add(\'hidden\'); }  function takePhoto() {     const video = document.getElementById(\'video\');     const canvas = document.getElementById(\'canvas\');     const context = canvas.getContext(\'2d\');      canvas.width = video.videoWidth;     canvas.height = video.videoHeight;     context.drawImage(video, 0, 0, canvas.width, canvas.height);      const imageData = canvas.toDataURL(\'image/png\');     tasks[currentTaskIndex].done = true;     tasks[currentTaskIndex].image = imageData;      saveAndRender();     closeCamera();     alert(`رائع يا ${userName}! تم توثيق المهمة بنجاح 🎉`); }  function saveAndRender() {     localStorage.setItem(\'myCleanTasksBadr\', JSON.stringify(tasks));     renderTasks(); }", null);
                // Custom CSS injection via Base64 to avoid quote escaping issues
                String cssStr = "body {     font-family: \'Segoe UI\', Tahoma, Geneva, Verdana, sans-serif;     background: linear-gradient(135deg, #1f4068 0%, #162447 50%, #1b1b2f 100%);     margin: 0;     padding: 15px;     color: #ffffff;     direction: rtl;     min-height: 100vh;     display: flex;     justify-content: center;     align-items: center; }  .app-container {     width: 100%;     max-width: 420px;     background: rgba(255, 255, 255, 0.95);     padding: 22px;     border-radius: 24px;     box-shadow: 0 15px 35px rgba(0,0,0,0.3);     color: #2c3e50;     backdrop-filter: blur(10px); }  .app-banner {     width: 100%;     height: 140px;     background: linear-gradient(135deg, #1f4068 0%, #162447 50%, #1b1b2f 100%);     border-radius: 16px;     display: flex;     flex-direction: column;     justify-content: center;     align-items: center;     text-align: center;     box-shadow: 0 8px 20px rgba(0,0,0,0.2);     margin-bottom: 15px;     border: 2px solid #0f4c81; } .app-banner-icon { font-size: 30px; margin-bottom: 3px; } .app-banner h2 { color: #ffffff; font-size: 21px; margin: 0; font-family: \'Segoe UI\', Tahoma, sans-serif; text-shadow: 0 2px 4px rgba(0,0,0,0.4); } .app-banner p { color: #a0aec0; font-size: 12px; margin: 4px 0 0 0; }  .screen {     display: flex;     flex-direction: column;     gap: 15px; }  .hidden {     display: none !important; }  .welcome-box {     text-align: center;     padding: 10px 10px;     display: flex;     flex-direction: column;     gap: 15px; } .welcome-icon { font-size: 45px; } .welcome-box h1 { color: #162447; margin: 0; font-size: 20px; } .welcome-box p { color: #555; font-size: 14px; margin: 0; } .welcome-box input {     padding: 14px;     border: 2px solid #ddd;     border-radius: 14px;     font-size: 16px;     text-align: center;     outline: none;     transition: 0.3s; } .welcome-box input:focus {     border-color: #0f4c81; }  .anime-guide-card {     background: #f3e5f5;     border: 2px solid #ab47bc;     padding: 12px;     border-radius: 16px;     display: flex;     align-items: center;     gap: 12px; }  .anime-avatar {     font-size: 32px;     background: #ffffff;     padding: 8px;     border-radius: 50%;     box-shadow: 0 2px 5px rgba(0,0,0,0.1); }  .anime-speech p {     margin: 0;     font-size: 13px;     color: #4a148c;     font-weight: bold;     line-height: 1.4; }  .energy-selector {     background: #f8f9fa;     padding: 12px;     border-radius: 14px;     border: 1px solid #e0e0e0;     display: flex;     flex-direction: column;     gap: 8px; }  .energy-title { font-size: 13px; font-weight: bold; color: #555; } .energy-buttons { display: flex; gap: 6px; }  .energy-btn {     flex: 1;     border: none;     padding: 9px 4px;     border-radius: 10px;     font-size: 11px;     font-weight: bold;     cursor: pointer;     background: #e9ecef;     color: #495057;     transition: 0.2s; }  .energy-btn.high.active { background: #ff5252; color: white; box-shadow: 0 4px 10px rgba(255,82,82,0.3); } .energy-btn.medium.active { background: #ffa726; color: white; box-shadow: 0 4px 10px rgba(255,167,38,0.3); } .energy-btn.low.active { background: #66bb6a; color: white; box-shadow: 0 4px 10px rgba(102,187,106,0.3); }  .stats-card { background: #e3f2fd; padding: 12px; border-radius: 14px; border: 1px solid #bbdefb; } .stat-info { display: flex; justify-content: space-between; align-items: center; font-size: 13px; font-weight: bold; color: #0d47a1; } .stat-info h2 { margin: 0; font-size: 16px; }  .progress-bar { background: #bbdefb; height: 8px; border-radius: 4px; overflow: hidden; margin-top: 8px; } .progress-fill { background: #1976d2; height: 100%; width: 0%; transition: width 0.3s ease; }  .main-add-btn { background: linear-gradient(135deg, #1f4068, #162447); color: white; border: none; padding: 14px; border-radius: 14px; font-size: 15px; font-weight: bold; cursor: pointer; box-shadow: 0 4px 15px rgba(22,36,71,0.2); } .section-title { font-weight: bold; font-size: 14px; color: #34495e; margin-top: 5px; }  ul { list-style: none; padding: 0; margin: 0; max-height: 220px; overflow-y: auto; }  li {     background: #f8f9fa;     padding: 12px;     margin-bottom: 10px;     border-radius: 12px;     display: flex;     flex-direction: column;     gap: 8px;     border-right: 5px solid #162447;     box-shadow: 0 2px 5px rgba(0,0,0,0.02); }  li.diff-high { border-right-color: #ff5252; } li.diff-medium { border-right-color: #ffa726; } li.diff-low { border-right-color: #66bb6a; }  .task-header { display: flex; justify-content: space-between; align-items: center; }  li.done { border-right-color: #4caf50 !important; background: #f1f8e9; } li.done .task-title { text-decoration: line-through; color: #888; }  .verify-btn { background-color: #ff9800; color: white; border: none; font-size: 12px; padding: 6px 12px; border-radius: 8px; cursor: pointer; font-weight: bold; } .proof-img { max-width: 100%; border-radius: 8px; border: 1px solid #ddd; }  .nav-bar { display: flex; align-items: center; gap: 10px; } .back-btn { background: #e9ecef; border: none; padding: 8px 14px; border-radius: 8px; cursor: pointer; font-weight: bold; } .form-group { display: flex; flex-direction: column; gap: 6px; } .form-group input { padding: 12px; border-radius: 10px; border: 1px solid #ccc; outline: none; font-size: 14px; }  .difficulty-selector { display: flex; gap: 8px; } .diff-option { flex: 1; padding: 10px; border-radius: 10px; font-size: 12px; font-weight: bold; text-align: center; cursor: pointer; border: 1px solid #ddd; background: #f9f9f9; }  .save-task-btn { background: #162447; color: white; border: none; padding: 14px; border-radius: 12px; font-size: 15px; font-weight: bold; cursor: pointer; } .reset-name-btn { background: #f8f9fa; color: #e53935; border: 1px solid #ffcdd2; padding: 8px; border-radius: 10px; font-size: 12px; cursor: pointer; margin-top: 5px; font-weight: bold; }  .modal { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.8); display: flex; justify-content: center; align-items: center; z-index: 1000; } .modal-content { background: white; padding: 20px; border-radius: 20px; width: 90%; max-width: 350px; text-align: center; color: #333; } video { width: 100%; border-radius: 12px; background: #000; } .buttons { display: flex; gap: 10px; margin-top: 12px; } .snap-btn { background: #4caf50; color: white; border: none; padding: 10px; border-radius: 10px; flex: 1; font-weight: bold; cursor: pointer; } .close-modal-btn { background: #e53935; color: white; border: none; padding: 10px; border-radius: 10px; cursor: pointer; font-weight: bold; }";
                String b64Css = android.util.Base64.encodeToString(cssStr.getBytes(), android.util.Base64.NO_WRAP);
                view.evaluateJavascript("(function(){var s=document.createElement('style');s.textContent=atob('" + b64Css + "');document.head.appendChild(s);})()", null);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleWebViewUrl(view, url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && request != null && request.getUrl() != null) {
                    return handleWebViewUrl(view, request.getUrl().toString());
                }
                return false;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (progressBar != null) progressBar.setProgress(newProgress);
            }
            
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                request.grant(request.getResources());
            }
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (fileUploadCallback != null) {
                    fileUploadCallback.onReceiveValue(null);
                }
                fileUploadCallback = filePathCallback;

                // Camera intent
                Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.TITLE, "camera_photo");
                cameraImageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);

                // File chooser intent
                Intent fileIntent = new Intent(Intent.ACTION_GET_CONTENT);
                fileIntent.addCategory(Intent.CATEGORY_OPENABLE);
                fileIntent.setType("*/*");

                // Combine into chooser
                Intent chooserIntent = Intent.createChooser(fileIntent, "Select file");
                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{cameraIntent});

                fileUploadLauncher.launch(chooserIntent);
                return true;
            }
        });

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimeType);
                String cookies = CookieManager.getInstance().getCookie(url);
                request.addRequestHeader("cookie", cookies);
                request.addRequestHeader("User-Agent", userAgent);
                request.setDescription("Downloading file...");
                request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType));
                request.allowScanningByMediaScanner();
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimeType));
                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                dm.enqueue(request);
                Toast.makeText(getApplicationContext(), "Downloading File", Toast.LENGTH_LONG).show();
            }
        });
    }


    private boolean handleWebViewUrl(WebView view, String url) {
        if (url == null || url.trim().isEmpty()) return false;
        String lower = url.toLowerCase();
        if (lower.startsWith("tel:") || lower.startsWith("mailto:") || lower.startsWith("sms:") || lower.startsWith("smsto:") || lower.startsWith("whatsapp:") || lower.startsWith("market:") || lower.startsWith("intent:")) {
            try {
                Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                startActivity(intent);
                return true;
            } catch (Exception ignored) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
                    return true;
                } catch (Exception ignoredAgain) {
                    return true;
                }
            }
        }
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return true;
        }
        view.loadUrl(url);
        return true;
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private void handleIntent(Intent intent) {
        if (intent != null && intent.getData() != null) {
            String deepUrl = intent.getData().toString();
            if (deepUrl.startsWith("http")) {
                webView.loadUrl(deepUrl);
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private final ActivityResultLauncher<Intent> fileUploadLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(), result -> {
            if (fileUploadCallback == null) return;
            if (result.getResultCode() == RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                fileUploadCallback.onReceiveValue(new Uri[]{result.getData().getData()});
            } else if (result.getResultCode() == RESULT_OK && cameraImageUri != null) {
                fileUploadCallback.onReceiveValue(new Uri[]{cameraImageUri});
            } else {
                fileUploadCallback.onReceiveValue(null);
            }
            fileUploadCallback = null;
        });

    private void setupBottomNav() {
        android.widget.FrameLayout rootLayout = (android.widget.FrameLayout) findViewById(android.R.id.content);
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setBackgroundColor(Color.parseColor("#1e293b"));
        bottomBar.setElevation(8 * getResources().getDisplayMetrics().density);
        
        android.widget.FrameLayout.LayoutParams bp = new android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT, (int)(56 * getResources().getDisplayMetrics().density));
        bp.gravity = android.view.Gravity.BOTTOM;
        
        String[][] tabs = {{"منصةمدرستي", "https://schools.madrasati.sa/"}};
        
        for (String[] tab : tabs) {
            TextView tv = new TextView(this);
            tv.setText(tab[0]);
            tv.setTextColor(Color.parseColor("#94a3b8"));
            tv.setTextSize(12);
            tv.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT);
            tp.weight = 1;
            final String url = tab[1];
            tv.setOnClickListener(v -> {
                if (url.equals("/") || url.isEmpty()) { webView.loadUrl(WEBSITE_URL); }
                else if (url.startsWith("http")) { webView.loadUrl(url); }
                else { webView.loadUrl(WEBSITE_URL + (WEBSITE_URL.endsWith("/") ? "" : "/") + url.replaceFirst("^/", "")); }
            });
            bottomBar.addView(tv, tp);
        }
        rootLayout.addView(bottomBar, bp);
        webView.setPadding(0, 0, 0, (int)(56 * getResources().getDisplayMetrics().density));
        webView.setClipToPadding(false);
    }

    private void checkForUpdate() {
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL("https://task-management-app-074327.netlify.app");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                conn.disconnect();
                
                JSONObject json = new JSONObject(sb.toString());
                String latestVersion = json.optString("latest_version", "");
                final String updateUrl = json.optString("update_url", "");
                
                if (!latestVersion.isEmpty() && !latestVersion.equals("1.0.0") && !updateUrl.isEmpty()) {
                    runOnUiThread(() -> {
                        new android.app.AlertDialog.Builder(MainActivity.this)
                            .setTitle("Update Available")
                            .setMessage("A new version (" + latestVersion + ") is available. Would you like to update?")
                            .setPositiveButton("Update", (d, w) -> {
                                startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(updateUrl)));
                            })
                            .setNegativeButton("Later", null)
                            .show();
                    });
                }
            } catch (Exception e) { /* silent fail */ }
        }).start();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (webView.canGoBack()) {
                webView.goBack();
                return true;
            } else {
                if (backPressedTime + 2000 > System.currentTimeMillis()) {
                    finish();
                } else {
                    android.widget.Toast.makeText(this, "Press back again to exit", android.widget.Toast.LENGTH_SHORT).show();
                    backPressedTime = System.currentTimeMillis();
                }
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "Share").setIcon(android.R.drawable.ic_menu_share)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        menu.add(0, 999, 0, "Privacy Policy");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, webView.getUrl());
            startActivity(Intent.createChooser(shareIntent, "Share via"));
            return true;
        }
        if (item.getItemId() == 999) {
            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://task-management-app-074327.netlify.app")));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // JavaScript Bridge - access via window.AndroidBridge in your web JS
    private class NativeBridge {
        @android.webkit.JavascriptInterface
        public void showToast(String message) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
        }

        @android.webkit.JavascriptInterface
        public void shareText(String text) {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, text);
            startActivity(Intent.createChooser(shareIntent, "Share"));
        }

        @android.webkit.JavascriptInterface
        public void openInBrowser(String url) {
            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
        }

        @android.webkit.JavascriptInterface
        public void copyToClipboard(String text) {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("text", text));
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Copied!", Toast.LENGTH_SHORT).show());
        }

        @android.webkit.JavascriptInterface
        public void vibrate(int ms) {
            android.os.Vibrator v = (android.os.Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) v.vibrate(ms);
        }

        @android.webkit.JavascriptInterface
        public String getAppVersion() {
            try {
                return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            } catch (Exception e) { return "unknown"; }
        }

        @android.webkit.JavascriptInterface
        public void dial(String phone) {
            startActivity(new Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + phone)));
        }

        @android.webkit.JavascriptInterface
        public void sendEmail(String to, String subject, String body) {
            Intent intent = new Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:" + to));
            intent.putExtra(Intent.EXTRA_SUBJECT, subject);
            intent.putExtra(Intent.EXTRA_TEXT, body);
            startActivity(intent);
        }

        @android.webkit.JavascriptInterface
        public void closeApp() {
            finish();
        }

        @android.webkit.JavascriptInterface
        public String getDeviceInfo() {
            return "{\"brand\":\"" + Build.BRAND + "\",\"model\":\"" + Build.MODEL + "\",\"sdk\":" + Build.VERSION.SDK_INT + ",\"android\":\"" + Build.VERSION.RELEASE + "\"}";
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webView != null) {
            webView.destroy();
        }
    }
}