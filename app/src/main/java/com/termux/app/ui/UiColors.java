package com.termux.app.ui;

import android.content.Context;
import android.util.TypedValue;

import androidx.annotation.AttrRes;

/** Resolves themed palette colors ({@code tx_*}), which differ between Dark and AMOLED. */
public final class UiColors {

    private UiColors() { }

    public static int attr(Context context, @AttrRes int attr) {
        TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(attr, value, true);
        return value.data;
    }
}
