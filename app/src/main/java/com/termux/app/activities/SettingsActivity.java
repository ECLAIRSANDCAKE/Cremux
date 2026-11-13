package com.termux.app.activities;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Bundle;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.termux.BuildConfig;
import com.termux.R;
import com.termux.app.ui.CommandTemplatesActivity;
import com.termux.app.ui.FileManagerActivity;
import com.termux.app.ui.PackageManagerActivity;
import com.termux.app.ui.RedesignActivity;
import com.termux.app.ui.TermuxPropertiesEditor;
import com.termux.app.ui.UiColors;
import com.termux.app.ui.UiPrefs;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.theme.TermuxThemeUtils;
import com.termux.shared.activities.ReportActivity;
import com.termux.shared.file.FileUtils;
import com.termux.shared.models.ReportInfo;
import com.termux.app.models.UserAction;
import com.termux.shared.interact.ShareUtils;
import com.termux.shared.android.PackageUtils;
import com.termux.shared.termux.settings.preferences.TermuxAPIAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxFloatAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxTaskerAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxWidgetAppSharedPreferences;
import com.termux.shared.android.AndroidUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxUtils;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.theme.NightMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SettingsActivity extends RedesignActivity {

    /** Show the classic, full preference tree instead of the redesigned overview. */
    public static final String EXTRA_ADVANCED = "com.termux.app.extra.settings_advanced";

    private static final class Group {
        View title;
        View card;
        final List<View> rows = new ArrayList<>();
        final List<String> rowTitles = new ArrayList<>();
    }

    private final List<Group> mGroups = new ArrayList<>();
    private LinearLayout mContent;
    private TextView mThemeValue, mCursorValue, mScrollbackValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getIntent().getBooleanExtra(EXTRA_ADVANCED, false)) {
            setContentView(R.layout.activity_settings_advanced);
            if (savedInstanceState == null) {
                getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new RootPreferencesFragment())
                    .commit();
            }

            AppCompatActivityUtils.setToolbar(this, com.termux.shared.R.id.toolbar);
            AppCompatActivityUtils.setShowBackButtonInActionBar(this, true);
        } else {
            buildRedesignedSettings();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    // ---- Redesigned overview ----

    private void buildRedesignedSettings() {
        setContentView(R.layout.activity_settings);
        setupHeader("Settings", this::filter);
        mContent = findViewById(R.id.settings_content);

        Group appearance = addGroup("Appearance");
        View themeRow = addRow(appearance, R.drawable.ic_contrast, "Theme", themeLabel(), v -> chooseTheme());
        mThemeValue = themeRow.findViewById(R.id.row_value);
        addSwitch(appearance, R.drawable.ic_blur, "Blur Effect", UiPrefs.getBoolean(this, UiPrefs.KEY_BLUR, true),
            checked -> UiPrefs.setBoolean(this, UiPrefs.KEY_BLUR, checked));

        Group terminal = addGroup("Terminal");
        mCursorValue = addRow(terminal, R.drawable.ic_cursor, "Cursor Style", cursorLabel(), v -> chooseCursor())
            .findViewById(R.id.row_value);
        mScrollbackValue = addRow(terminal, R.drawable.ic_list, "Scrollback Lines", scrollbackValue(), v -> chooseScrollback())
            .findViewById(R.id.row_value);

        Group tools = addGroup("Tools");
        addRow(tools, R.drawable.ic_prompt, "Command Templates", null,
            v -> startActivity(new Intent(this, CommandTemplatesActivity.class)));
        addRow(tools, R.drawable.ic_cube, "Package Manager", null,
            v -> startActivity(new Intent(this, PackageManagerActivity.class)));
        addRow(tools, R.drawable.ic_folder_outline, "File Manager", null,
            v -> startActivity(new Intent(this, FileManagerActivity.class)));

        Group about = addGroup("About");
        View versionRow = addRow(about, R.drawable.ic_tag, "Version", BuildConfig.VERSION_NAME, v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("version", BuildConfig.VERSION_NAME));
            Toast.makeText(this, "Version copied", Toast.LENGTH_SHORT).show();
        });
        versionRow.findViewById(R.id.row_chevron).setVisibility(View.GONE);
        addRow(about, R.drawable.ic_info, "About Termux", null, v -> showAbout());
        addRow(about, R.drawable.ic_globe, "Help", null, v -> startActivity(new Intent(this, HelpActivity.class)));

        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(this, false);
        boolean notifications = prefs == null || prefs.areCrashReportNotificationsEnabled(false);
        addSwitch(about, R.drawable.ic_bell, "Enable Notifications", notifications, checked -> {
            TermuxAppSharedPreferences p = TermuxAppSharedPreferences.build(this, false);
            if (p != null) {
                p.setCrashReportNotificationsEnabled(checked);
                p.setPluginErrorNotificationsEnabled(checked);
            }
        });

        Group advanced = addGroup("Advanced");
        addRow(advanced, R.drawable.ic_tune, "All settings", null,
            v -> startActivity(new Intent(this, SettingsActivity.class).putExtra(EXTRA_ADVANCED, true)));
    }

    private Group addGroup(String title) {
        Group g = new Group();
        TextView header = (TextView) getLayoutInflater().inflate(R.layout.item_setting_section, mContent, false);
        header.setText(title);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        mContent.addView(header);
        mContent.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        g.title = header;
        g.card = card;
        mGroups.add(g);
        return g;
    }

    private View addRow(Group g, int icon, String title, String value, View.OnClickListener click) {
        LinearLayout card = (LinearLayout) g.card;
        View row = getLayoutInflater().inflate(R.layout.item_setting_row, card, false);
        ((ImageView) row.findViewById(R.id.row_icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.row_title)).setText(title);
        TextView valueView = row.findViewById(R.id.row_value);
        if (value == null) valueView.setVisibility(View.GONE); else valueView.setText(value);
        row.setOnClickListener(click);
        appendRow(g, row, title);
        return row;
    }

    private interface Toggle { void onToggle(boolean checked); }

    private void addSwitch(Group g, int icon, String title, boolean checked, Toggle toggle) {
        LinearLayout card = (LinearLayout) g.card;
        View row = getLayoutInflater().inflate(R.layout.item_setting_row, card, false);
        ((ImageView) row.findViewById(R.id.row_icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.row_title)).setText(title);
        row.findViewById(R.id.row_value).setVisibility(View.GONE);
        row.findViewById(R.id.row_chevron).setVisibility(View.GONE);
        SwitchCompat sw = row.findViewById(R.id.row_switch);
        sw.setVisibility(View.VISIBLE);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener((b, isChecked) -> toggle.onToggle(isChecked));
        row.setOnClickListener(v -> sw.toggle());
        appendRow(g, row, title);
    }

    private void appendRow(Group g, View row, String title) {
        LinearLayout card = (LinearLayout) g.card;
        if (!g.rows.isEmpty()) {
            View divider = new View(this);
            divider.setBackgroundColor(UiColors.attr(this, R.attr.tx_stroke));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
            lp.setMarginStart(dp(54));
            card.addView(divider, lp);
            row.setTag(divider);
        }
        card.addView(row);
        g.rows.add(row);
        g.rowTitles.add(title.toLowerCase(Locale.ROOT));
    }

    private void filter(String query) {
        String q = query.toLowerCase(Locale.ROOT);
        for (Group g : mGroups) {
            boolean any = false;
            for (int i = 0; i < g.rows.size(); i++) {
                boolean match = q.isEmpty() || g.rowTitles.get(i).contains(q);
                g.rows.get(i).setVisibility(match ? View.VISIBLE : View.GONE);
                Object divider = g.rows.get(i).getTag();
                if (divider instanceof View) ((View) divider).setVisibility(match ? View.VISIBLE : View.GONE);
                any |= match;
            }
            g.title.setVisibility(any ? View.VISIBLE : View.GONE);
            g.card.setVisibility(any ? View.VISIBLE : View.GONE);
        }
    }

    private String themeLabel() {
        if (UiPrefs.isAmoled(this)) return "AMOLED Black";
        String mode = NightMode.getAppNightMode().getName();
        if ("true".equals(mode)) return "Dark";
        if ("false".equals(mode)) return "Light";
        return "System";
    }

    private void chooseTheme() {
        final String[] labels = {"System", "Light", "Dark", "AMOLED Black"};
        final String[] values = {"system", "false", "true", "true"};
        UiPrefs.show(this, new AlertDialog.Builder(this).setTitle("Theme").setItems(labels, (d, which) -> {
            if (TermuxPropertiesEditor.set("night-mode", values[which])) {
                TermuxThemeUtils.setAppNightMode(values[which]);
                UiPrefs.setBoolean(this, UiPrefs.KEY_AMOLED, which == 3);
                if (mThemeValue != null) mThemeValue.setText(labels[which]);
                recreate();
            } else {
                Toast.makeText(this, "Could not write termux.properties", Toast.LENGTH_SHORT).show();
            }
        }));
    }

    private String cursorLabel() {
        String v = TermuxPropertiesEditor.get("terminal-cursor-style");
        if ("bar".equals(v)) return "Bar";
        if ("underline".equals(v)) return "Underline";
        return "Block";
    }

    private void chooseCursor() {
        final String[] labels = {"Block", "Bar", "Underline"};
        final String[] values = {"block", "bar", "underline"};
        UiPrefs.show(this, new AlertDialog.Builder(this).setTitle("Cursor Style").setItems(labels, (d, which) -> {
            if (TermuxPropertiesEditor.set("terminal-cursor-style", values[which])) {
                mCursorValue.setText(labels[which]);
            }
        }));
    }

    private String scrollbackValue() {
        String v = TermuxPropertiesEditor.get("terminal-transcript-rows");
        return v == null || v.isEmpty() ? "2000" : v;
    }

    private void chooseScrollback() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(scrollbackValue());
        input.setSelectAllOnFocus(true);
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(dp(20), dp(8), dp(20), 0);
        wrap.addView(input);
        UiPrefs.show(this, new AlertDialog.Builder(this)
            .setTitle("Scrollback Lines")
            .setMessage("100 – 50000. Applies to new sessions.")
            .setView(wrap)
            .setPositiveButton(android.R.string.ok, (d, w) -> {
                int rows;
                try {
                    rows = Integer.parseInt(input.getText().toString().trim());
                } catch (NumberFormatException e) {
                    return;
                }
                rows = Math.max(100, Math.min(50000, rows));
                if (TermuxPropertiesEditor.set("terminal-transcript-rows", String.valueOf(rows))) {
                    mScrollbackValue.setText(String.valueOf(rows));
                }
            })
            .setNegativeButton(android.R.string.cancel, null));
    }

    private void showAbout() {
        final Context context = this;
        new Thread() {
            @Override
            public void run() {
                String title = "About";

                StringBuilder aboutString = new StringBuilder();
                aboutString.append(TermuxUtils.getAppInfoMarkdownString(context, TermuxUtils.AppInfoMode.TERMUX_AND_PLUGIN_PACKAGES));
                aboutString.append("\n\n").append(AndroidUtils.getDeviceInfoMarkdownString(context, true));
                aboutString.append("\n\n").append(TermuxUtils.getImportantLinksMarkdownString(context));

                String userActionName = UserAction.ABOUT.getName();

                ReportInfo reportInfo = new ReportInfo(userActionName,
                    TermuxConstants.TERMUX_APP.TERMUX_SETTINGS_ACTIVITY_NAME, title);
                reportInfo.setReportString(aboutString.toString());
                reportInfo.setReportSaveFileLabelAndPath(userActionName,
                    Environment.getExternalStorageDirectory() + "/" +
                        FileUtils.sanitizeFileName(TermuxConstants.TERMUX_APP_NAME + "-" + userActionName + ".log", true, true));

                ReportActivity.startReportActivity(context, reportInfo);
            }
        }.start();
    }

    public static class RootPreferencesFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            Context context = getContext();
            if (context == null) return;

            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            new Thread() {
                @Override
                public void run() {
                    configureTermuxAPIPreference(context);
                    configureTermuxFloatPreference(context);
                    configureTermuxTaskerPreference(context);
                    configureTermuxWidgetPreference(context);
                    configureAboutPreference(context);
                    configureDonatePreference(context);
                }
            }.start();
        }

        private void configureTermuxAPIPreference(@NonNull Context context) {
            Preference termuxAPIPreference = findPreference("termux_api");
            if (termuxAPIPreference != null) {
                TermuxAPIAppSharedPreferences preferences = TermuxAPIAppSharedPreferences.build(context, false);
                // If failed to get app preferences, then likely app is not installed, so do not show its preference
                termuxAPIPreference.setVisible(preferences != null);
            }
        }

        private void configureTermuxFloatPreference(@NonNull Context context) {
            Preference termuxFloatPreference = findPreference("termux_float");
            if (termuxFloatPreference != null) {
                TermuxFloatAppSharedPreferences preferences = TermuxFloatAppSharedPreferences.build(context, false);
                // If failed to get app preferences, then likely app is not installed, so do not show its preference
                termuxFloatPreference.setVisible(preferences != null);
            }
        }

        private void configureTermuxTaskerPreference(@NonNull Context context) {
            Preference termuxTaskerPreference = findPreference("termux_tasker");
            if (termuxTaskerPreference != null) {
                TermuxTaskerAppSharedPreferences preferences = TermuxTaskerAppSharedPreferences.build(context, false);
                // If failed to get app preferences, then likely app is not installed, so do not show its preference
                termuxTaskerPreference.setVisible(preferences != null);
            }
        }

        private void configureTermuxWidgetPreference(@NonNull Context context) {
            Preference termuxWidgetPreference = findPreference("termux_widget");
            if (termuxWidgetPreference != null) {
                TermuxWidgetAppSharedPreferences preferences = TermuxWidgetAppSharedPreferences.build(context, false);
                // If failed to get app preferences, then likely app is not installed, so do not show its preference
                termuxWidgetPreference.setVisible(preferences != null);
            }
        }

        private void configureAboutPreference(@NonNull Context context) {
            Preference aboutPreference = findPreference("about");
            if (aboutPreference != null) {
                aboutPreference.setOnPreferenceClickListener(preference -> {
                    new Thread() {
                        @Override
                        public void run() {
                            String title = "About";

                            StringBuilder aboutString = new StringBuilder();
                            aboutString.append(TermuxUtils.getAppInfoMarkdownString(context, TermuxUtils.AppInfoMode.TERMUX_AND_PLUGIN_PACKAGES));
                            aboutString.append("\n\n").append(AndroidUtils.getDeviceInfoMarkdownString(context, true));
                            aboutString.append("\n\n").append(TermuxUtils.getImportantLinksMarkdownString(context));

                            String userActionName = UserAction.ABOUT.getName();

                            ReportInfo reportInfo = new ReportInfo(userActionName,
                                TermuxConstants.TERMUX_APP.TERMUX_SETTINGS_ACTIVITY_NAME, title);
                            reportInfo.setReportString(aboutString.toString());
                            reportInfo.setReportSaveFileLabelAndPath(userActionName,
                                Environment.getExternalStorageDirectory() + "/" +
                                    FileUtils.sanitizeFileName(TermuxConstants.TERMUX_APP_NAME + "-" + userActionName + ".log", true, true));

                            ReportActivity.startReportActivity(context, reportInfo);
                        }
                    }.start();

                    return true;
                });
            }
        }

        private void configureDonatePreference(@NonNull Context context) {
            Preference donatePreference = findPreference("donate");
            if (donatePreference != null) {
                String signingCertificateSHA256Digest = PackageUtils.getSigningCertificateSHA256DigestForPackage(context);
                if (signingCertificateSHA256Digest != null) {
                    // If APK is a Google Playstore release, then do not show the donation link
                    // since Termux isn't exempted from the playstore policy donation links restriction
                    // Check Fund solicitations: https://pay.google.com/intl/en_in/about/policy/
                    String apkRelease = TermuxUtils.getAPKRelease(signingCertificateSHA256Digest);
                    if (apkRelease == null || apkRelease.equals(TermuxConstants.APK_RELEASE_GOOGLE_PLAYSTORE_SIGNING_CERTIFICATE_SHA256_DIGEST)) {
                        donatePreference.setVisible(false);
                        return;
                    } else {
                        donatePreference.setVisible(true);
                    }
                }

                donatePreference.setOnPreferenceClickListener(preference -> {
                    ShareUtils.openUrl(context, TermuxConstants.TERMUX_DONATE_URL);
                    return true;
                });
            }
        }
    }

}
