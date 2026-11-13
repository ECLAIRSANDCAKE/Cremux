package com.termux.app.ui;

import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.termux.R;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties;
import com.termux.shared.termux.theme.TermuxThemeUtils;
import com.termux.shared.theme.NightMode;

/** Base class of the redesigned screens: night mode, system bars and the shared header bar. */
public abstract class RedesignActivity extends AppCompatActivity {

    public interface QueryListener {
        void onQuery(@NonNull String query);
    }

    private View mHeaderSearch;
    private View mHeaderNormal;
    private EditText mSearchInput;
    private QueryListener mQueryListener;
    private String mAppliedNightMode;
    private boolean mAppliedAmoled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mAppliedAmoled = UiPrefs.isAmoled(this);
        if (mAppliedAmoled) setTheme(R.style.Theme_TermuxRedesign_Amoled);

        TermuxAppSharedProperties properties = TermuxAppSharedProperties.getProperties();
        if (properties != null) {
            properties.loadTermuxPropertiesFromDisk();
            TermuxThemeUtils.setAppNightMode(properties.getNightMode());
        }
        super.onCreate(savedInstanceState);
        mAppliedNightMode = NightMode.getAppNightMode().getName();
        AppCompatActivityUtils.setNightMode(this, mAppliedNightMode, true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mAppliedAmoled != UiPrefs.isAmoled(this)) {
            recreate();
            return;
        }
        // Pick up a theme change made on another screen (this triggers a recreate).
        String now = NightMode.getAppNightMode().getName();
        if (mAppliedNightMode != null && !now.equals(mAppliedNightMode)) {
            mAppliedNightMode = now;
            AppCompatActivityUtils.setNightMode(this, now, true);
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        applySystemBars();
    }

    private void applySystemBars() {
        boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
            == Configuration.UI_MODE_NIGHT_YES;
        View decor = getWindow().getDecorView();
        int flags = decor.getSystemUiVisibility();
        if (!night) {
            if (Build.VERSION.SDK_INT >= 23) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags |= 0x10; // SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        } else {
            if (Build.VERSION.SDK_INT >= 23) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags &= ~0x10;
        }
        decor.setSystemUiVisibility(flags);
    }

    /**
     * Wire up the shared header ({@code partial_header}).
     *
     * @param title    The title text.
     * @param listener Called on every query change; {@code null} hides the search button.
     */
    protected void setupHeader(String title, QueryListener listener) {
        mQueryListener = listener;
        mHeaderNormal = findViewById(R.id.header_normal);
        mHeaderSearch = findViewById(R.id.header_search);
        mSearchInput = findViewById(R.id.header_search_input);
        ((TextView) findViewById(R.id.header_title)).setText(title);
        findViewById(R.id.btn_back).setOnClickListener(v -> onBackPressed());

        View searchButton = findViewById(R.id.btn_header_search);
        if (listener == null) {
            searchButton.setVisibility(View.INVISIBLE);
            return;
        }
        searchButton.setOnClickListener(v -> openSearch());
        findViewById(R.id.btn_search_close).setOnClickListener(v -> closeSearch());
        mSearchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                if (mQueryListener != null) mQueryListener.onQuery(s.toString().trim());
            }
        });
    }

    protected void openSearch() {
        if (mHeaderSearch == null) return;
        mHeaderNormal.setVisibility(View.GONE);
        mHeaderSearch.setVisibility(View.VISIBLE);
        mSearchInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(mSearchInput, InputMethodManager.SHOW_IMPLICIT);
    }

    protected void closeSearch() {
        if (mHeaderSearch == null) return;
        mSearchInput.setText("");
        mHeaderSearch.setVisibility(View.GONE);
        mHeaderNormal.setVisibility(View.VISIBLE);
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(mSearchInput.getWindowToken(), 0);
    }

    protected boolean isSearchOpen() {
        return mHeaderSearch != null && mHeaderSearch.getVisibility() == View.VISIBLE;
    }

    @Override
    public void onBackPressed() {
        if (isSearchOpen()) {
            closeSearch();
        } else {
            super.onBackPressed();
        }
    }

    protected int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
