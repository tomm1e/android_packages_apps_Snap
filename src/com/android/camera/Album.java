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

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import com.android.camera.ui.RotateTextToast;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.TypedValue;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

public class Album {
    public String TAG = "Album";

    private final Context context;

    private static Album smInstance;

    public static final int ALBUM_TYPE_DEFAULT = 0;
    public static final int ALBUM_TYPE_DATE = 1;
    public static final int ALBUM_TYPE_CUSTOM = 2;
    public static final int ALBUM_TYPE_HIDDEN = 3;

    private String sDefaultDateFormat = "yyyy-MM-dd";
    private String sDefaultCustom = "Custom";
    private int sDefaultAlbumType = ALBUM_TYPE_DEFAULT;

    private SimpleDateFormat sdfAlbumDateFormat;

    // Preferences
    private int sAlbumType = sDefaultAlbumType;
    private String sAlbumDateFormat = sDefaultDateFormat;
    private String sAlbumCustom = sDefaultCustom;

    private Album(Context context) {
        this.context = context;
    }

    public static void initialize(Context context) {
        if (smInstance == null) {
            smInstance = new Album(context);
        }
    }

    public static synchronized Album instance() {
        return smInstance;
    }

    public boolean setAlbumCustom(String input) {
        input = input.replaceAll("\\.","");
        input = input.replaceAll("/","");
        input = input.replaceAll("\\\\","");
        input = input.trim();

        if (input.isEmpty()) {
            Log.w(TAG, "Input for custom album name is empty.");
            return false;
        }

        sAlbumCustom = input;
        sAlbumType = ALBUM_TYPE_CUSTOM;

        savePreferences();

        return true;
    }

    public void setAlbumDateFormat(String format) {
        sAlbumDateFormat = format;
        savePreferences();
    }

    public void setAlbumType(int type) {
        sAlbumType = type;
        savePreferences();
    }

    public String getAlbumDate() {
        Calendar rightNow = Calendar.getInstance();

        return sdfAlbumDateFormat.format(rightNow.getTimeInMillis());
    }

    public int getAlbumType() {
        return sAlbumType;
    }

    public String getAlbumCustom() {
        return sAlbumCustom;
    }

    public String getAlbumPath() {
        int type = getAlbumType();
        String path = "DCIM/Camera/";
        String sep = "/";
        switch (type) {
            case ALBUM_TYPE_DATE:
                path += getAlbumDate() + sep;
            break;
            case ALBUM_TYPE_CUSTOM:
                path += getAlbumCustom() + sep;
            break;
            case ALBUM_TYPE_HIDDEN:
                path += "Hidden" + sep;
            break;
        }

        return path;
    }

    public boolean isAlbumHidden() {
        return (getAlbumType() == ALBUM_TYPE_HIDDEN);
    }

    public void resetPreferences() {
        sAlbumType = sDefaultAlbumType;
        sAlbumDateFormat = sDefaultDateFormat;
        sAlbumCustom = sDefaultCustom;
        savePreferences();
    }

    public void loadPreferences() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        // sAlbumDateFormat = prefs.getString("sm-dateformat", sDefaultDateFormat);
        sAlbumCustom = prefs.getString("sm-albumcustom", "Custom");
        sAlbumType = prefs.getInt("sm-albumtype", ALBUM_TYPE_DEFAULT);
        sdfAlbumDateFormat = new SimpleDateFormat(sDefaultDateFormat);
    }

    public void savePreferences() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putInt("sm-albumtype", sAlbumType).apply();
        // prefs.edit().putString("sm-dateformat", sAlbumDateFormat).apply();
        prefs.edit().putString("sm-albumcustom", sAlbumCustom).apply();
    }

    public boolean handleAlbumPreferenceAction(ListPreference pref, CameraActivity act) {
        if (pref != null && CameraSettings.KEY_ALBUM.equals(pref.getKey())) {
            int albumType = Integer.valueOf(pref.getValue());
            setAlbumType(albumType);
            if (albumType == Album.ALBUM_TYPE_CUSTOM) {
                showAlbumCustomInputDialog(act);
                return true;
            }
        }
        return false;
    }

    public String makeAlbumPath(String title) {
        String storePath = Storage.DIRECTORY + '/' + getAlbumPath();
        String storePathDefault = Storage.DIRECTORY;

        if (Storage.isSaveSDCard() && SDCard.instance().isWriteable()) {
            storePath = SDCard.instance().getDirectory() + '/' + getAlbumPath();
            storePathDefault = SDCard.instance().getDirectory();
        }

        File f = new File(storePath);
        try {
            if (f.mkdirs() || f.exists()) {
                // Create .nomedia file for hidden
                if (isAlbumHidden()) {
                    File nmFile = new File(storePath + "/.nomedia");
                    if (!nmFile.exists()) {
                        try {
                            nmFile.createNewFile();
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to create nomedia file.", e);
                        }
                    }
                }
                return storePath + title;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Failed to create subdirs, store to default
        Log.w(TAG, "Failed to store into subdirectory.");

        return storePathDefault + '/' + title;
    }

    public void showAlbumCustomInputDialog(CameraActivity act) {
        // build dialog
        final AlertDialog.Builder alert = new AlertDialog.Builder(act);
        final EditText input = new EditText(act);
        final LinearLayout linear = new LinearLayout(act);

        linear.setOrientation(LinearLayout.VERTICAL);

        int x = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,20,
            act.getResources().getDisplayMetrics());

        linear.setPadding(x,0,x,0);

        input.setText(sAlbumCustom);

        alert.setTitle("Custom Album Name");
        // alert.setMessage("Location:");

        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
            }
        });

        alert.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {

                String c = input.getText().toString();

                if (setAlbumCustom(c)) {
                    RotateTextToast.makeText(act, "Set album to " +
                        sAlbumCustom, Toast.LENGTH_SHORT).show();
                } else {
                    RotateTextToast.makeText(act, "Empty or invalid input.",
                        Toast.LENGTH_SHORT).show();
                }
            }
        });

        linear.addView(input);
        alert.setView(linear);
        alert.show();
    }
}
