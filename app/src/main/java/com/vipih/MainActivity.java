package com.vipih;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private TextView tv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tv = new TextView(this);
        tv.setText("Connecting to Telegram API...\nPlease wait.");
        tv.setTextSize(20);
        setContentView(tv);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String urlStr = "https://api.telegram.org/bot8855944219:AAHTCpO73cxzcfvzwHxze0WCWSoHytzRBsg/sendMessage";
                    URL url = new URL(urlStr);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(10000); // انتظار 10 ثواني كحد أقصى
                    conn.setReadTimeout(10000);
                    conn.setRequestProperty("Content-Type", "application/json");

                    String jsonInputString = "{\"chat_id\": \"8197183486\", \"text\": \"Test Message From App\"}";

                    try (OutputStream os = conn.getOutputStream()) {
                        byte[] input = jsonInputString.getBytes("utf-8");
                        os.write(input, 0, input.length);
                    }

                    int responseCode = conn.getResponseCode();
                    final String result = "HTTP Code: " + responseCode + "\n\n" + (responseCode == 200 ? "Success! Check Telegram." : "Failed! Check Token or Chat ID.");

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tv.setText(result);
                        }
                    });

                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tv.setText("Network Error:\n" + e.getMessage() + "\n\nPlease turn on VPN and try again.");
                        }
                    });
                }
            }
        }).start();
    }
}