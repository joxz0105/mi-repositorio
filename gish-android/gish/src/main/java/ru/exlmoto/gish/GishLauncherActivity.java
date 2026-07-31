/************************************************************************************
** The MIT License (MIT)
**
** Copyright (c) 2017 EXL
**
** Permission is hereby granted, free of charge, to any person obtaining a copy
** of this software and associated documentation files (the "Software"), to deal
** in the Software without restriction, including without limitation the rights
** to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
** copies of the Software, and to permit persons to whom the Software is
** furnished to do so, subject to the following conditions:
**
** The above copyright notice and this permission notice shall be included in all
** copies or substantial portions of the Software.
**
** THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
** IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
** FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
** AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
** LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
** OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
** SOFTWARE.
************************************************************************************/

package ru.exlmoto.gish;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class GishLauncherActivity extends Activity {

    private static final int AC_FILE_PICKER_CODE = 1;
    private static final int AC_IMPORT_ZIP_CODE = 2;
    private static final int AC_IMPORT_TREE_CODE = 3;

    private static final int RC_READ_STORAGE = 100;

    private static final int VIBRO_MIN = 5;
    private static final int VIBRO_MAX = 200;
    private static final int VIBRO_DEFAULT = 30;

    private static final float ZOOM_MIN = 4.0f;
    private static final float ZOOM_MAX = 50.0f;
    private static final float ZOOM_DEFAULT = 10.0f;

    public static class GishSettings {

        public static final int NO_TOUCH_CONTROLS = 0;
        public static final int OLD_TOUCH_CONTROLS = 1;
        public static final int MODERN_TOUCH_CONTROLS = 2;

        // GAME SETTINGS
        public static int touchControls = MODERN_TOUCH_CONTROLS;
        public static boolean sound = true;          // Access from JNI
        public static boolean music = true;          // Access from JNI
        public static boolean showFps = false;       // Access from JNI
        public static boolean fixCache = false;      // Access from JNI
        public static boolean joyAccel = false;      // Access from JNI
        public static boolean openGles = true;
        public static boolean lights = false;        // Access from JNI
        public static boolean shadows = false;       // Access from JNI
        public static boolean touchVibration = true;
        public static boolean gameVibration = true;
        public static int vibroScale = VIBRO_DEFAULT;
        public static String gishDataSavedPath = ""; // Access from JNI
        public static float zoom = ZOOM_DEFAULT;     // Access from JNI
        public static boolean borderOff = false;     // Access from JNI
    }

    private boolean firstRun = false;

    private EditText editTextDataPath = null;
    private EditText editTextHaptics = null;
    private EditText editTextZoom = null;
    private CheckBox checkBoxSound = null;
    private CheckBox checkBoxMusic = null;
    private CheckBox checkBoxJoyAccel = null;
    private CheckBox checkBoxFixCache = null;
    private CheckBox checkBoxShowFps = null;
    private CheckBox checkBoxLights = null;
    private CheckBox checkBoxShadows = null;
    private CheckBox checkBoxVibrationInGame = null;
    private CheckBox checkBoxVibrationOnTouch = null;
    private CheckBox checkBoxBorderOff = null;
    private RadioButton radioButtonModernTouchControls = null;
    private RadioButton radioButtonSimpleTouchControls = null;
    private RadioButton radioButtonNoTouchControls = null;
    private RadioButton radioGles = null;
    private RadioButton radioGl4es = null;

    private AlertDialog aboutDialog = null;
    private AlertDialog progressDialog = null;

    private final ExecutorService importExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public SharedPreferences mSharedPreferences = null;
    GishLauncherActivity gishLauncherActivity = this;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (mSharedPreferences == null) {
            mSharedPreferences = getSharedPreferences("ru.exlmoto.gish", MODE_PRIVATE);
        }

        // Check the first run
        if (mSharedPreferences.getBoolean("firstrun", true)) {
            // The first run, fill GUI layout with default values
            mSharedPreferences.edit().putBoolean("firstrun", false).apply();
            firstRun = true;
            // Point at the managed data directory instead of leaving the path
            // empty; it needs no permission and is where imports land.
            GishSettings.gishDataSavedPath =
                    GishStorage.asEnginePath(GishStorage.getDefaultDataDir(this));
        } else {
            firstRun = false;
            // Read settings from Shared Preferences
            // Move to onResume()
            // readSettings();
        }

        initAboutDialog();

        setContentView(R.layout.gish_launcher);

        initWidgets();

        // Move to onResume()
        // fillWidgetsBySettings();

        checkBoxSound.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                GishSettings.sound = isChecked;
                if (!isChecked) {
                    GishSettings.music = false;
                    checkBoxMusic.setChecked(false);
                    checkBoxMusic.setEnabled(false);
                } else {
                    checkBoxMusic.setEnabled(true);
                }
            }
        });

        checkBoxMusic.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                GishSettings.music = isChecked;
            }
        });

        checkBoxJoyAccel.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                GishSettings.joyAccel = isChecked;
            }
        });

        checkBoxBorderOff.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                GishSettings.borderOff = isChecked;
            }
        });

        checkBoxFixCache.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                GishSettings.fixCache = isChecked;
            }
        });

        checkBoxShowFps.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                GishSettings.showFps = isChecked;
            }
        });

        checkBoxLights.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                GishSettings.lights = isChecked;
            }
        });

        checkBoxShadows.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                GishSettings.shadows = isChecked;
            }
        });

        radioButtonModernTouchControls.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                GishSettings.touchControls = GishSettings.MODERN_TOUCH_CONTROLS;
            }
        });

        radioButtonSimpleTouchControls.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                GishSettings.touchControls = GishSettings.OLD_TOUCH_CONTROLS;
            }
        });

        radioButtonNoTouchControls.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                GishSettings.touchControls = GishSettings.NO_TOUCH_CONTROLS;
            }
        });

        checkBoxVibrationOnTouch.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                GishSettings.touchVibration = isChecked;
                if (!isChecked && !checkBoxVibrationInGame.isChecked()) {
                    editTextHaptics.setText(String.valueOf(VIBRO_DEFAULT));
                    editTextHaptics.setEnabled(false);
                } else {
                    editTextHaptics.setEnabled(true);
                }
            }
        });

        checkBoxVibrationInGame.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                GishSettings.gameVibration = isChecked;
                if (!isChecked && !checkBoxVibrationOnTouch.isChecked()) {
                    editTextHaptics.setText(String.valueOf(VIBRO_DEFAULT));
                    editTextHaptics.setEnabled(false);
                } else {
                    editTextHaptics.setEnabled(true);
                }
            }
        });

        Button buttonRun = (Button) findViewById(R.id.buttonRun);
        buttonRun.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (testAllRanges()) {
                    writeSettings();

                    if (GishStorage.isValidDataPath(GishSettings.gishDataSavedPath)) {
                        Intent intent = new Intent(gishLauncherActivity, GishActivity.class);
                        startActivity(intent);
                    } else {
                        showMissingDataDialog();
                    }
                }
            }
        });

        Button buttonBrowse = (Button) findViewById(R.id.buttonBrowse);
        buttonBrowse.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                writeSettings();
                showDataSourceChooser();
            }
        });

        Button buttonResetAllSettings = (Button) findViewById(R.id.buttonResetSettings);
        buttonResetAllSettings.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                showResetQuestionDialog();
            }
        });

        Button buttonAbout = (Button) findViewById(R.id.buttonAbout);
        buttonAbout.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                writeSettings();
                showMyDialog(aboutDialog);
            }
        });
    }

    @Override
    protected void onResume() {
        if (!firstRun) {
            readSettings();
        }
        fillWidgetsBySettings();
        super.onResume();
    }

    // ------------------------------------------------------------ data sources

    /**
     * Android 11 removed File-API access to shared storage for non-media files,
     * so pointing the engine at an arbitrary /sdcard path no longer works there.
     * The user picks how to get the data into app-specific storage instead.
     */
    private void showDataSourceChooser() {
        final List<String> labels = new ArrayList<String>();
        final List<Integer> actions = new ArrayList<Integer>();

        labels.add(getString(R.string.import_zip));
        actions.add(AC_IMPORT_ZIP_CODE);

        labels.add(getString(R.string.import_folder));
        actions.add(AC_IMPORT_TREE_CODE);

        if (GishStorage.isLegacyBrowsingUsable()) {
            labels.add(getString(R.string.import_browse));
            actions.add(AC_FILE_PICKER_CODE);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.import_title);
        builder.setItems(labels.toArray(new String[0]), new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (actions.get(which)) {
                case AC_IMPORT_ZIP_CODE:
                    launchZipPicker();
                    break;
                case AC_IMPORT_TREE_CODE:
                    launchTreePicker();
                    break;
                case AC_FILE_PICKER_CODE:
                    requestLegacyBrowse();
                    break;
                default:
                    break;
                }
            }
        });
        builder.setNegativeButton(R.string.button_cancel, null);
        builder.show();
    }

    private void launchZipPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResultSafely(intent, AC_IMPORT_ZIP_CODE);
    }

    private void launchTreePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResultSafely(intent, AC_IMPORT_TREE_CODE);
    }

    private void startActivityForResultSafely(Intent intent, int requestCode) {
        // Deliberately not pre-checking with resolveActivity(): from API 30 the
        // package-visibility rules can hide the system picker from that query
        // even though launching it works fine.
        try {
            startActivityForResult(intent, requestCode);
        } catch (android.content.ActivityNotFoundException e) {
            showToast(R.string.import_unsupported, Toast.LENGTH_LONG);
        }
    }

    private void requestLegacyBrowse() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[] { Manifest.permission.READ_EXTERNAL_STORAGE }, RC_READ_STORAGE);
            return;
        }
        runFilePicker();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != RC_READ_STORAGE) {
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            runFilePicker();
        } else {
            showToast(R.string.permission_denied, Toast.LENGTH_LONG);
        }
    }

    private void runFilePicker() {
        Intent intent = new Intent(this, GishFilePickerActivity.class);
        startActivityForResult(intent, AC_FILE_PICKER_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            return;
        }
        switch (requestCode) {
        case AC_FILE_PICKER_CODE:
            GishSettings.gishDataSavedPath = data.getStringExtra("GishPath");
            editTextDataPath.setText(GishSettings.gishDataSavedPath);
            writeSettings();
            showToast(R.string.data_path_c, Toast.LENGTH_SHORT);
            break;
        case AC_IMPORT_ZIP_CODE:
            if (data.getData() != null) {
                startImport(data.getData(), true);
            }
            break;
        case AC_IMPORT_TREE_CODE:
            if (data.getData() != null) {
                startImport(data.getData(), false);
            }
            break;
        default:
            break;
        }
    }

    /**
     * Copies the picked archive or folder into app-specific storage off the main
     * thread; imports routinely move hundreds of megabytes.
     */
    private void startImport(final Uri source, final boolean isZip) {
        final File destination = GishStorage.getDefaultDataDir(this);
        showProgressDialog();

        importExecutor.execute(new Runnable() {

            @Override
            public void run() {
                String error = null;
                try {
                    if (isZip) {
                        GishStorage.importFromZip(gishLauncherActivity, source, destination);
                    } else {
                        GishStorage.importFromTree(gishLauncherActivity, source, destination);
                    }
                } catch (Exception e) {
                    error = String.valueOf(e.getMessage());
                    GishActivity.toDebugLog("Import failed: " + error);
                }

                final String finalError = error;
                mainHandler.post(new Runnable() {

                    @Override
                    public void run() {
                        onImportFinished(destination, finalError);
                    }
                });
            }
        });
    }

    private void onImportFinished(File destination, String error) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        dismissProgressDialog();

        if (error != null) {
            showToast(R.string.import_failed, Toast.LENGTH_LONG);
            return;
        }
        if (!GishStorage.isValidDataDir(destination)) {
            // The copy succeeded but the content was not a Gish data folder.
            showToast(R.string.data_path_wrong, Toast.LENGTH_LONG);
            return;
        }

        GishSettings.gishDataSavedPath = GishStorage.asEnginePath(destination);
        editTextDataPath.setText(GishSettings.gishDataSavedPath);
        writeSettings();
        showToast(R.string.import_ok, Toast.LENGTH_SHORT);
    }

    private void showProgressDialog() {
        ProgressBar bar = new ProgressBar(this);
        bar.setIndeterminate(true);
        int padding = getResources().getDimensionPixelSize(R.dimen.layout_margin_x2);
        bar.setPadding(padding, padding, padding, padding);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.importing);
        builder.setView(bar);
        builder.setCancelable(false);
        progressDialog = builder.show();
    }

    private void dismissProgressDialog() {
        if (progressDialog != null) {
            progressDialog.dismiss();
            progressDialog = null;
        }
    }

    // ---------------------------------------------------------------- dialogs

    private boolean testAllRanges() {
        int haptics = parseIntSafely(editTextHaptics.getText().toString(), Integer.MIN_VALUE);
        if (haptics < VIBRO_MIN || haptics > VIBRO_MAX) {
            showRangeDialog(R.string.wrong_haptic_title, R.string.wrong_haptic_body,
                    String.valueOf(VIBRO_MIN), String.valueOf(VIBRO_MAX), true);
            return false;
        }
        float zoom = parseFloatSafely(editTextZoom.getText().toString(), Float.NaN);
        if (Float.isNaN(zoom) || zoom < ZOOM_MIN || zoom > ZOOM_MAX) {
            showRangeDialog(R.string.wrong_zoom_title, R.string.wrong_zoom_body,
                    String.valueOf(ZOOM_MIN), String.valueOf(ZOOM_MAX), false);
            return false;
        }
        return true;
    }

    private void showRangeDialog(int titleId, int bodyId, String min, String max,
                                 final boolean isHaptics) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(titleId));
        builder.setMessage(getString(bodyId) + "\nMin: " + min + "\nMax: " + max + "\n"
                + getString(R.string.wrong_body_general));
        builder.setPositiveButton(getString(R.string.button_ok), new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int id) {
                if (isHaptics) {
                    GishSettings.vibroScale = VIBRO_DEFAULT;
                    editTextHaptics.setText(String.valueOf(GishSettings.vibroScale));
                } else {
                    GishSettings.zoom = ZOOM_DEFAULT;
                    editTextZoom.setText(String.valueOf(GishSettings.zoom));
                }
                dialog.cancel();
            }
        });
        builder.show();
    }

    private void showResetQuestionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.question_title_general));
        builder.setMessage(getString(R.string.question_reset_body));
        builder.setPositiveButton(getString(R.string.button_ok), new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int id) {
                resetAllSettingsToDefaultValues();
                dialog.cancel();
            }
        });
        builder.setNegativeButton(getString(R.string.button_cancel), new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
            }
        });
        builder.show();
    }

    private void showMissingDataDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.data_path_wrong);
        builder.setMessage(getString(R.string.data_path_help,
                GishStorage.getDefaultDataDir(this).getAbsolutePath()));
        builder.setPositiveButton(R.string.button_ok, null);
        builder.show();
    }

    private void resetAllSettingsToDefaultValues() {
        // Delete user profiles and config
        GishStorage.deleteRecursive(new File(getFilesDir(), ".gish"));

        GishSettings.touchControls = GishSettings.MODERN_TOUCH_CONTROLS;
        GishSettings.sound = true;
        GishSettings.music = true;
        GishSettings.showFps = false;
        GishSettings.fixCache = false;
        GishSettings.joyAccel = false;
        GishSettings.borderOff = false;
        GishSettings.openGles = true;
        GishSettings.lights = false;
        GishSettings.shadows = false;
        GishSettings.touchVibration = true;
        GishSettings.gameVibration = true;
        GishSettings.vibroScale = VIBRO_DEFAULT;
        // Keep pointing at the managed directory: resetting settings should not
        // throw away an import the user may have waited minutes for.
        GishSettings.gishDataSavedPath =
                GishStorage.asEnginePath(GishStorage.getDefaultDataDir(this));
        GishSettings.zoom = ZOOM_DEFAULT;

        fillWidgetsBySettings();

        showToast(R.string.reset_game, Toast.LENGTH_SHORT);
    }

    private void showToast(int stringId, int length) {
        Toast.makeText(gishLauncherActivity, getResources().getString(stringId), length).show();
    }

    @Override
    protected void onDestroy() {
        writeSettings();
        dismissProgressDialog();
        importExecutor.shutdownNow();

        super.onDestroy();
    }

    private void initAboutDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        final ViewGroup nullParentVG = null;
        View dialogView = inflater.inflate(R.layout.gish_dialog_about, nullParentVG);
        builder.setView(dialogView);
        builder.setTitle(R.string.app_name);
        builder.setPositiveButton(R.string.button_ok, null);
        aboutDialog = builder.create();
    }

    // Prevent dialog dismiss when orientation changes
    // http://stackoverflow.com/a/27311231/2467443
    private static void doKeepDialog(AlertDialog dialog) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.copyFrom(dialog.getWindow().getAttributes());
        lp.width = WindowManager.LayoutParams.WRAP_CONTENT;
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        dialog.getWindow().setAttributes(lp);
    }

    private void showMyDialog(AlertDialog dialog) {
        dialog.show();
        doKeepDialog(dialog);
    }

    // ---------------------------------------------------------------- settings

    /**
     * An empty or malformed field used to throw NumberFormatException straight
     * out of onDestroy(), crashing the app on exit.
     */
    private static int parseIntSafely(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static float parseFloatSafely(String value, float fallback) {
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void fillSettingsByWidgets() {
        GishActivity.toDebugLog("FSBW!");
        GishSettings.showFps = checkBoxShowFps.isChecked();
        GishSettings.fixCache = checkBoxFixCache.isChecked();
        GishSettings.joyAccel = checkBoxJoyAccel.isChecked();
        GishSettings.borderOff = checkBoxBorderOff.isChecked();
        GishSettings.lights = checkBoxLights.isChecked();
        GishSettings.shadows = checkBoxShadows.isChecked();
        GishSettings.touchVibration = checkBoxVibrationOnTouch.isChecked();
        GishSettings.gameVibration = checkBoxVibrationInGame.isChecked();
        GishSettings.vibroScale = parseIntSafely(editTextHaptics.getText().toString(), VIBRO_DEFAULT);
        GishSettings.gishDataSavedPath = editTextDataPath.getText().toString();
        GishSettings.zoom = parseFloatSafely(editTextZoom.getText().toString(), ZOOM_DEFAULT);
        if (radioButtonModernTouchControls.isChecked()) {
            GishSettings.touchControls = GishSettings.MODERN_TOUCH_CONTROLS;
        } else if (radioButtonSimpleTouchControls.isChecked()) {
            GishSettings.touchControls = GishSettings.OLD_TOUCH_CONTROLS;
        } else if (radioButtonNoTouchControls.isChecked()) {
            GishSettings.touchControls = GishSettings.NO_TOUCH_CONTROLS;
        }
        if (radioGles.isChecked()) {
            GishSettings.openGles = true;
        } else if (radioGl4es.isChecked()) {
            GishSettings.openGles = false;
        }
        GishSettings.sound = checkBoxSound.isChecked();
        if (!GishSettings.sound) {
            GishSettings.music = false;
        } else {
            GishSettings.music = checkBoxMusic.isChecked();
        }
    }

    private void fillWidgetsBySettings() {
        GishActivity.toDebugLog("FWBS!");
        editTextDataPath.setText(GishSettings.gishDataSavedPath);
        editTextHaptics.setText(String.valueOf(GishSettings.vibroScale));
        editTextZoom.setText(String.valueOf(GishSettings.zoom));
        checkBoxJoyAccel.setChecked(GishSettings.joyAccel);
        checkBoxBorderOff.setChecked(GishSettings.borderOff);
        checkBoxFixCache.setChecked(GishSettings.fixCache);
        checkBoxShowFps.setChecked(GishSettings.showFps);
        checkBoxLights.setChecked(GishSettings.lights);
        checkBoxShadows.setChecked(GishSettings.shadows);
        checkBoxVibrationOnTouch.setChecked(GishSettings.touchVibration);
        checkBoxVibrationInGame.setChecked(GishSettings.gameVibration);
        switch (GishSettings.touchControls) {
        case GishSettings.MODERN_TOUCH_CONTROLS:
            radioButtonModernTouchControls.setChecked(true);
            break;
        case GishSettings.OLD_TOUCH_CONTROLS:
            radioButtonSimpleTouchControls.setChecked(true);
            break;
        case GishSettings.NO_TOUCH_CONTROLS:
            radioButtonNoTouchControls.setChecked(true);
            break;
        default:
            break;
        }
        if (GishSettings.openGles) {
            radioGles.setChecked(true);
        } else {
            radioGl4es.setChecked(true);
        }
        editTextHaptics.setEnabled(checkBoxVibrationInGame.isChecked()
                || checkBoxVibrationOnTouch.isChecked());
        checkBoxSound.setChecked(GishSettings.sound);
        if (!GishSettings.sound) {
            checkBoxMusic.setChecked(false);
            checkBoxMusic.setEnabled(false);
        } else {
            checkBoxMusic.setChecked(GishSettings.music);
            checkBoxMusic.setEnabled(true);
        }
    }

    private void initWidgets() {
        editTextDataPath = (EditText) findViewById(R.id.editTextDataPath);
        editTextHaptics = (EditText) findViewById(R.id.editTextVibroScale);
        editTextZoom = (EditText) findViewById(R.id.editTextZoom);
        checkBoxSound = (CheckBox) findViewById(R.id.checkBoxSound);
        checkBoxMusic = (CheckBox) findViewById(R.id.checkBoxMusic);
        checkBoxJoyAccel = (CheckBox) findViewById(R.id.checkBoxJoyAccel);
        checkBoxBorderOff = (CheckBox) findViewById(R.id.checkBoxBorderOff);
        checkBoxFixCache = (CheckBox) findViewById(R.id.checkBoxFixCache);
        checkBoxShowFps = (CheckBox) findViewById(R.id.checkBoxFps);
        checkBoxLights = (CheckBox) findViewById(R.id.checkBoxLights);
        checkBoxShadows = (CheckBox) findViewById(R.id.checkBoxShadows);
        checkBoxVibrationOnTouch = (CheckBox) findViewById(R.id.checkBoxVibroOnTouch);
        checkBoxVibrationInGame = (CheckBox) findViewById(R.id.checkBoxVibtoInGame);
        radioButtonModernTouchControls = (RadioButton) findViewById(R.id.radioButtonModern);
        radioButtonSimpleTouchControls = (RadioButton) findViewById(R.id.radioButtonSimple);
        radioButtonNoTouchControls = (RadioButton) findViewById(R.id.radioButtonOff);
        radioGles = (RadioButton) findViewById(R.id.radioButtonGles);
        radioGl4es = (RadioButton) findViewById(R.id.radioButtonGl4es);
    }

    public void writeSettings() {
        fillSettingsByWidgets();

        SharedPreferences.Editor editor = mSharedPreferences.edit();
        editor.putInt("touchControls", GishSettings.touchControls);
        editor.putInt("vibroScale", GishSettings.vibroScale);
        editor.putBoolean("sound", GishSettings.sound);
        editor.putBoolean("music", GishSettings.music);
        editor.putBoolean("showFps", GishSettings.showFps);
        editor.putBoolean("joyAccel", GishSettings.joyAccel);
        editor.putBoolean("borderOff", GishSettings.borderOff);
        editor.putBoolean("fixCache", GishSettings.fixCache);
        editor.putBoolean("openGles", GishSettings.openGles);
        editor.putBoolean("lights", GishSettings.lights);
        editor.putBoolean("shadows", GishSettings.shadows);
        editor.putBoolean("touchVibration", GishSettings.touchVibration);
        editor.putBoolean("gameVibration", GishSettings.gameVibration);
        editor.putString("dataSavedPath", GishSettings.gishDataSavedPath);
        editor.putFloat("zoom", GishSettings.zoom);
        editor.apply();
    }

    public void readSettings() {
        GishActivity.toDebugLog("Read Settings!");

        GishSettings.touchControls = mSharedPreferences.getInt("touchControls", GishSettings.MODERN_TOUCH_CONTROLS);
        GishSettings.vibroScale = mSharedPreferences.getInt("vibroScale", VIBRO_DEFAULT);
        GishSettings.sound = mSharedPreferences.getBoolean("sound", true);
        GishSettings.music = mSharedPreferences.getBoolean("music", true);
        GishSettings.showFps = mSharedPreferences.getBoolean("showFps", false);
        GishSettings.joyAccel = mSharedPreferences.getBoolean("joyAccel", false);
        GishSettings.borderOff = mSharedPreferences.getBoolean("borderOff", false);
        GishSettings.fixCache = mSharedPreferences.getBoolean("fixCache", false);
        GishSettings.openGles = mSharedPreferences.getBoolean("openGles", true);
        GishSettings.lights = mSharedPreferences.getBoolean("lights", false);
        GishSettings.shadows = mSharedPreferences.getBoolean("shadows", false);
        GishSettings.touchVibration = mSharedPreferences.getBoolean("touchVibration", true);
        GishSettings.gameVibration = mSharedPreferences.getBoolean("gameVibration", true);
        GishSettings.gishDataSavedPath = mSharedPreferences.getString("dataSavedPath",
                GishStorage.asEnginePath(GishStorage.getDefaultDataDir(this)));
        GishSettings.zoom = mSharedPreferences.getFloat("zoom", ZOOM_DEFAULT);
    }
}
