package com.termux.app.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;

/** Preferences and visual effects private to the redesigned UI. */
public final class UiPrefs {

    private static final String FILE = "redesign_prefs";
    public static final String KEY_BLUR = "ui_blur";
    public static final String KEY_AMOLED = "ui_amoled";
    public static final String KEY_NOTIFICATIONS = "ui_notifications";

    private UiPrefs() { }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean getBoolean(Context c, String key, boolean def) {
        return prefs(c).getBoolean(key, def);
    }

    public static void setBoolean(Context c, String key, boolean value) {
        prefs(c).edit().putBoolean(key, value).apply();
    }

    public static boolean isAmoled(Context c) {
        return getBoolean(c, KEY_AMOLED, false);
    }

    /** Show a dialog, applying the blur-behind effect on Android 12+ when enabled. */
    public static Dialog show(@NonNull Context context, @NonNull AlertDialog.Builder builder) {
        AlertDialog dialog = builder.create();
        applyBlur(context, dialog);
        dialog.show();
        return dialog;
    }

    public static void applyBlur(Context context, Dialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) return;
        boolean blur = getBoolean(context, KEY_BLUR, true);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setDimAmount(blur ? 0.35f : 0.6f);
        if (blur && Build.VERSION.SDK_INT >= 31) {
            try {
                window.addFlags(0x00000004); // FLAG_BLUR_BEHIND
                WindowManager.LayoutParams lp = window.getAttributes();
                WindowManager.LayoutParams.class.getMethod("setBlurBehindRadius", int.class).invoke(lp, 40);
                window.setAttributes(lp);
            } catch (Exception ignored) {
                // Blur is purely cosmetic.
            }
        }
    }
}
