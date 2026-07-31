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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;

import androidx.core.content.ContextCompat;

import org.libsdl.app.SDLActivity;

import ru.exlmoto.gish.GishLauncherActivity.GishSettings;

/**
 * Created by exl on 5/14/17.
 */

public class GishActivity extends SDLActivity {

    private static final String APP_TAG = "Gish_app";

    private static Activity m_GishActivity = null;
    private GishTouchControlsView gishTouchControlsView = null;

    private static Vibrator m_vibrator = null;

    // --- SDL Patch Functions
    public static void pressOrReleaseKey(int keyCode, boolean press) {
        if (press) {
            SDLActivity.onNativeKeyDown(keyCode);
        } else {
            SDLActivity.onNativeKeyUp(keyCode);
        }
    }

    public static boolean convertKeysFilter(int keyCode, boolean press) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                pressOrReleaseKey(KeyEvent.KEYCODE_DPAD_UP, press);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                pressOrReleaseKey(KeyEvent.KEYCODE_DPAD_DOWN, press);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                pressOrReleaseKey(KeyEvent.KEYCODE_DPAD_LEFT, press);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                pressOrReleaseKey(KeyEvent.KEYCODE_DPAD_RIGHT, press);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
                pressOrReleaseKey(KeyEvent.KEYCODE_ENTER, press);
                return true;
            case KeyEvent.KEYCODE_BACK:
                pressOrReleaseKey(KeyEvent.KEYCODE_ESCAPE, press);
                return true;
            default:
                return false;
        }
    }

    public static void toDebugLog(String message) {
        Log.d(APP_TAG, message);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        m_GishActivity = this;

        m_vibrator = obtainVibrator(this);

        if (GishSettings.touchControls == GishSettings.MODERN_TOUCH_CONTROLS) {
            gishTouchControlsView = new GishTouchControlsView(this);
            addContentView(gishTouchControlsView,
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.MATCH_PARENT));
        } else if (GishSettings.touchControls == GishSettings.OLD_TOUCH_CONTROLS) {
            LinearLayout ll = new LinearLayout(this);
            // setBackgroundDrawable + Resources.getDrawable(int) are both removed
            // from the modern API surface.
            ll.setBackground(ContextCompat.getDrawable(this, R.drawable.overlay_controls));
            addContentView(ll, new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT));
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        applyImmersiveMode();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            // The system bars come back on their own after a swipe or a dialog;
            // re-hiding them on focus is what keeps the game fullscreen.
            applyImmersiveMode();
        }
    }

    /**
     * Hides the status and navigation bars. SDL 2.0.5 predates both APIs used
     * here, so the port has to drive immersive mode itself.
     */
    private void applyImmersiveMode() {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    /** VIBRATOR_SERVICE is deprecated in favour of VibratorManager on API 31+. */
    private static Vibrator obtainVibrator(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager manager =
                    (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            return manager != null ? manager.getDefaultVibrator() : null;
        }
        return (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }

    // JNI-method
    public static void doVibrate(int duration, int fromJNI) {
        // From JNI: 1 -- yes, 0 -- no
        // 30 is default scale for vibration
        if (m_vibrator == null || !m_vibrator.hasVibrator()) {
            return;
        }

        boolean enabled = (fromJNI == 1) ? GishSettings.gameVibration : GishSettings.touchVibration;
        if (!enabled) {
            return;
        }

        long millis = duration + (GishSettings.vibroScale - 30);
        if (millis <= 0) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            m_vibrator.vibrate(
                    VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            m_vibrator.vibrate(millis);
        }
    }

    // JNI-function
    public static void openUrl(String aUrl) {
        Activity activity = m_GishActivity;
        if (activity == null) {
            return;
        }
        Uri uriUrl = Uri.parse(aUrl);
        Intent intent = new Intent(Intent.ACTION_VIEW, uriUrl);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            activity.startActivity(intent);
        } catch (android.content.ActivityNotFoundException e) {
            // No browser installed; the game must not die over an "about" link.
            toDebugLog("No activity can open " + aUrl);
        }
    }

    @Override
    protected void onDestroy() {
        if (m_GishActivity == this) {
            m_GishActivity = null;
            m_vibrator = null;
        }
        super.onDestroy();
    }
}
