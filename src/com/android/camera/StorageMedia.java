/*
 * Copyright (C) 2012 The Android Open Source Project
 * Copyright (C) 2013-2015 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.camera;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;

import com.android.camera.exif.ExifInterface;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.MediaStore.MediaColumns;
import android.util.Log;

public class StorageMedia {
    public String TAG = "StorageMedia";

    private final Context context;

    private static StorageMedia smInstance;
    
    private String sDefaultVolume = "external_primary";
    private String sCurrentVolume = sDefaultVolume;

    private StorageMedia(Context context) {
        this.context = context;
    }

    public static void initialize(Context context) {
        if (smInstance == null) {
            smInstance = new StorageMedia(context);
        }
    }

    public static synchronized StorageMedia instance() {
        return smInstance;
    }

    public String getSaveVolume() {
        return sCurrentVolume;
    }

    public Set<String> getVolumes() {
        return MediaStore.getExternalVolumeNames(context);
    }

    public void setInternal() {
        sCurrentVolume = sDefaultVolume;
        savePreferences();
    }

    public boolean setExternal() {
        Set<String> volumes = getVolumes();
        if (volumes.size() == 1) {
            return false;
        } else {
            sCurrentVolume = (String) volumes.toArray()[1];
            sCurrentVolume = sCurrentVolume.toLowerCase();
            Log.d(TAG, "Set external volume to: " + sCurrentVolume);
            savePreferences();
            return true;
        }
    }

    public void resetPreferences() {
        sCurrentVolume = sDefaultVolume;
        savePreferences();
    }

    public void loadPreferences() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);        
        sCurrentVolume = prefs.getString("sm-volume", sDefaultVolume);
    }

    public void savePreferences() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putString("sm-volume", sCurrentVolume).apply();
    }

    public ContentValues buildImageContentValues(String title, long date,
        Location location, int orientation, ExifInterface exif, int jpegLength,
        int width, int height, String mimeType) {

        ContentValues values = new ContentValues();
        values.put(ImageColumns.TITLE, title);
        if (mimeType.equalsIgnoreCase("jpeg") || mimeType.equalsIgnoreCase("image/jpeg") ||
                mimeType.equalsIgnoreCase("heif") || mimeType == null) {
            if (mimeType.equalsIgnoreCase("heif")){
                values.put(ImageColumns.DISPLAY_NAME, title + ".heic");
            } else if(mimeType.equalsIgnoreCase("heifs")){
                values.put(ImageColumns.DISPLAY_NAME, title + ".heics");
            } else {
                values.put(ImageColumns.DISPLAY_NAME, title + ".jpg");
            }
        } else {
            values.put(ImageColumns.DISPLAY_NAME, title + ".raw");
        }
        if (mimeType.equalsIgnoreCase("heif")) {
            values.put(ImageColumns.MIME_TYPE, "image/heif");
        } else {
            values.put(ImageColumns.MIME_TYPE, "image/jpeg");
        }

        values.put(ImageColumns.DATE_TAKEN, date);
        values.put(ImageColumns.ORIENTATION, orientation);
        values.put(ImageColumns.SIZE, jpegLength);        
        values.put(ImageColumns.RELATIVE_PATH, Album.instance().getAlbumPath());
        values.put(MediaColumns.WIDTH, width);
        values.put(MediaColumns.HEIGHT, height);

        if (Album.instance().getAlbumType() == Album.ALBUM_TYPE_HIDDEN) {
            values.put(MediaColumns.IS_PENDING, 1);
            values.put(MediaColumns.DATE_EXPIRES, Long.MAX_VALUE);
        }

        return values;
    }

    public Uri addImage(ContentResolver resolver, String title, long date,
        Location location, int orientation, ExifInterface exif, byte[] jpeg, int width,
        int height, String mimeType) {
        OutputStream fos;

        Uri contentUri = MediaStore.Images.Media.getContentUri(getSaveVolume());

        ContentValues contentValues = buildImageContentValues(title, date, location,
            orientation, exif, jpeg.length, width, height, mimeType);

        if (location != null) {
            exif.addGpsTags(location.getLatitude(), location.getLongitude());
        }

        Uri imageUri = resolver.insert(contentUri, contentValues);
        if (imageUri != null) {
            try {
                fos = resolver.openOutputStream(imageUri);
                try {
                    exif.writeExif(jpeg, fos);
                } catch (IOException e) {
                    Log.e(TAG, "EXIF write failed.", e);
                    fos.write(jpeg);
                } finally {
                    fos.close();
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to addImage", e);
            }
        }
        return imageUri;
    }
}
