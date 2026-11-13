package com.termux.app.ui;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.TermuxService;
import com.termux.app.activities.SettingsActivity;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.terminal.TerminalSession;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The Home screen: environment status, quick actions and recent sessions. */
public class DashboardActivity extends RedesignActivity {

    private TermuxService mService;
    private boolean mBound = false;

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            mService = ((TermuxService.LocalBinder) binder).service;
            refresh();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            refresh();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        findViewById(R.id.btn_search).setOnClickListener(v ->
            startActivity(new Intent(this, PackageManagerActivity.class).putExtra("focus_search", true)));
        findViewById(R.id.btn_settings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.status_card).setOnClickListener(v -> TerminalCommands.openTerminal(this));

        findViewById(R.id.qa_update).setOnClickListener(v -> TerminalCommands.run(this, "pkg update && pkg upgrade -y"));
        findViewById(R.id.qa_install).setOnClickListener(v -> startActivity(new Intent(this, PackageManagerActivity.class)));
        findViewById(R.id.qa_storage).setOnClickListener(v -> TerminalCommands.run(this, "termux-setup-storage"));
        findViewById(R.id.qa_sessions).setOnClickListener(v -> showSessionsDialog());
        findViewById(R.id.quick_see_all).setOnClickListener(v -> startActivity(new Intent(this, CommandTemplatesActivity.class)));
        findViewById(R.id.sessions_see_all).setOnClickListener(v -> showSessionsDialog());

        findViewById(R.id.nav_terminal).setOnClickListener(v -> TerminalCommands.openTerminal(this));
        findViewById(R.id.nav_sync).setOnClickListener(v -> startActivity(new Intent(this, GitHubSyncActivity.class)));
        findViewById(R.id.nav_files).setOnClickListener(v -> startActivity(new Intent(this, FileManagerActivity.class)));
        findViewById(R.id.nav_settings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Bind only if the service is already running; Home never starts it by itself.
        try {
            mBound = bindService(new Intent(this, TermuxService.class), mConnection, 0);
        } catch (Exception e) {
            mBound = false;
        }
        refresh();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mBound) {
            try {
                unbindService(mConnection);
            } catch (Exception ignored) { }
            mBound = false;
        }
        mService = null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @NonNull
    private List<TerminalSession> sessions() {
        List<TerminalSession> out = new ArrayList<>();
        if (mService == null) return out;
        for (TermuxSession ts : new ArrayList<>(mService.getTermuxSessions())) {
            if (ts.getTerminalSession() != null) out.add(ts.getTerminalSession());
        }
        Collections.sort(out, (a, b) -> Long.compare(RecentSessions.lastActive(b), RecentSessions.lastActive(a)));
        return out;
    }

    private void refresh() {
        boolean installed = new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/bin/login").exists();
        List<TerminalSession> sessions = sessions();

        TextView title = findViewById(R.id.status_title);
        TextView subtitle = findViewById(R.id.status_subtitle);
        View dot = findViewById(R.id.status_dot);
        if (!installed) {
            title.setText("Setup required");
            subtitle.setText("Tap to install the environment");
            dot.setBackgroundResource(R.drawable.dot_orange);
        } else {
            title.setText("Ready");
            subtitle.setText(sessions.isEmpty() ? "Environment installed — tap to open a terminal" : "Environment is active");
            dot.setBackgroundResource(R.drawable.dot_green);
        }

        android.widget.LinearLayout container = findViewById(R.id.recent_sessions);
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        if (sessions.isEmpty()) {
            View row = inflater.inflate(R.layout.item_recent_session, container, false);
            ((TextView) row.findViewById(R.id.rs_name)).setText("No sessions yet — start one");
            ((TextView) row.findViewById(R.id.rs_time)).setText("");
            row.setOnClickListener(v -> TerminalCommands.openTerminal(this));
            container.addView(row);
            return;
        }
        for (int i = 0; i < Math.min(3, sessions.size()); i++) {
            TerminalSession s = sessions.get(i);
            View row = inflater.inflate(R.layout.item_recent_session, container, false);
            ((TextView) row.findViewById(R.id.rs_name)).setText(displayName(s, indexOf(s)));
            ((TextView) row.findViewById(R.id.rs_time)).setText(timeAgo(s));
            row.setOnClickListener(v -> TerminalCommands.openSession(this, s.mHandle));
            container.addView(row);
        }
    }

    private int indexOf(TerminalSession s) {
        if (mService == null) return 0;
        List<TermuxSession> all = new ArrayList<>(mService.getTermuxSessions());
        for (int i = 0; i < all.size(); i++) if (all.get(i).getTerminalSession() == s) return i;
        return 0;
    }

    private static String displayName(TerminalSession s, int index) {
        String name = s.mSessionName;
        if (name != null && !name.isEmpty()) return name;
        return "Session " + (index + 1);
    }

    private static String timeAgo(TerminalSession s) {
        long t = RecentSessions.lastActive(s);
        if (t == 0) return "";
        if (System.currentTimeMillis() - t < DateUtils.MINUTE_IN_MILLIS) return "just now";
        return DateUtils.getRelativeTimeSpanString(t, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE).toString();
    }

    // ---- Sessions manager ----

    private void showSessionsDialog() {
        final List<TerminalSession> sessions = sessions();
        List<String> labels = new ArrayList<>();
        for (TerminalSession s : sessions) {
            String label = displayName(s, indexOf(s));
            String ago = timeAgo(s);
            if (!ago.isEmpty()) label += "  ·  " + ago;
            if (!s.isRunning()) label += "  (finished)";
            labels.add(label);
        }
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle("Sessions")
            .setNeutralButton("New session", (d, w) -> startActivity(new Intent(this, com.termux.app.TermuxActivity.class)
                .putExtra(TerminalCommands.EXTRA_NEW_SESSION, true)))
            .setNegativeButton(android.R.string.cancel, null);
        if (labels.isEmpty()) {
            b.setMessage("No sessions are running. Tap Terminal to start one.");
            UiPrefs.show(this, b);
            return;
        }
        ListView list = new ListView(this);
        list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels));
        b.setView(list);
        final android.app.Dialog dialog = UiPrefs.show(this, b);
        list.setOnItemClickListener((p, v, pos, id) -> {
            dialog.dismiss();
            TerminalCommands.openSession(this, sessions.get(pos).mHandle);
        });
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            dialog.dismiss();
            showSessionOptions(sessions.get(pos));
            return true;
        });
    }

    private void showSessionOptions(TerminalSession s) {
        String[] items = {"Open", "Rename", s.isRunning() ? "Close session" : "Remove session"};
        UiPrefs.show(this, new AlertDialog.Builder(this).setTitle(displayName(s, indexOf(s)))
            .setItems(items, (d, which) -> {
                if (which == 0) {
                    TerminalCommands.openSession(this, s.mHandle);
                } else if (which == 1) {
                    EditText input = new EditText(this);
                    input.setSingleLine(true);
                    input.setText(s.mSessionName == null ? "" : s.mSessionName);
                    FrameLayout wrap = new FrameLayout(this);
                    wrap.setPadding(dp(20), dp(8), dp(20), 0);
                    wrap.addView(input, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    UiPrefs.show(this, new AlertDialog.Builder(this).setTitle("Rename session").setView(wrap)
                        .setPositiveButton(android.R.string.ok, (dd, ww) -> {
                            s.mSessionName = input.getText().toString().trim();
                            refresh();
                        })
                        .setNegativeButton(android.R.string.cancel, null));
                } else {
                    if (s.isRunning()) s.finishIfRunning();
                    else if (mService != null) mService.removeTermuxSession(s);
                    refresh();
                }
            }));
    }
}
