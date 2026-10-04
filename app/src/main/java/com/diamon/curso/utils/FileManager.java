package com.diamon.curso.utils;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class FileManager {
    private static final String TAG = "FileManager";

    public static boolean exportToDownloads(Context context, String fileName) {
        File sourceFile = new File(context.getFilesDir(), fileName);
        if (!sourceFile.exists()) {
            Log.e(TAG, "Archivo de origen no existe: " + fileName);
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            values.put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream");
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/FlashromApp");

            Uri externalUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            Uri fileUri = context.getContentResolver().insert(externalUri, values);

            if (fileUri != null) {
                try (InputStream inStream = new FileInputStream(sourceFile);
                     OutputStream outStream = context.getContentResolver().openOutputStream(fileUri)) {
                    if (outStream instanceof FileOutputStream) {
                        try (java.nio.channels.FileChannel inChannel = ((FileInputStream) inStream).getChannel();
                             java.nio.channels.FileChannel outChannel = ((FileOutputStream) outStream).getChannel()) {
                            long size = inChannel.size();
                            long transferred = 0;
                            while (transferred < size) {
                                transferred += inChannel.transferTo(transferred, size - transferred, outChannel);
                            }
                            return true;
                        }
                    } else {
                        byte[] buffer = new byte[65536];
                        int read;
                        while ((read = inStream.read(buffer)) != -1) {
                            outStream.write(buffer, 0, read);
                        }
                        return true;
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error exportando via MediaStore", e);
                }
            }
        } else {
            // Para versiones antiguas de Android
            File destFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "FlashromApp");
            if (!destFolder.exists() && !destFolder.mkdirs())
                return false;

            File destFile = new File(destFolder, fileName);
            try (java.nio.channels.FileChannel inChannel = new FileInputStream(sourceFile).getChannel();
                 java.nio.channels.FileChannel outChannel = new FileOutputStream(destFile).getChannel()) {
                long size = inChannel.size();
                long transferred = 0;
                while (transferred < size) {
                    transferred += inChannel.transferTo(transferred, size - transferred, outChannel);
                }
                return true;
            } catch (IOException e) {
                Log.e(TAG, "Error exportando via File API", e);
            }
        }
        return false;
    }
}
