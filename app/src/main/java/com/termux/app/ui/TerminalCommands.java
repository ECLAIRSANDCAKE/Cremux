package com.termux.app.ui;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import com.termux.app.TermuxActivity;

/** Helpers to hand work over to the terminal screen. */
public final class TerminalCommands {

    /** Extra holding a shell command that {@link TermuxActivity} types into the current session. */
    public static final String EXTRA_RUN_COMMAND = "com.termux.app.extra.run_command";
    /** Extra holding the handle of a session that {@link TermuxActivity} should switch to. */
    public static final String EXTRA_SWITCH_SESSION = "com.termux.app.extra.switch_session";

    /** Boolean extra: {@link TermuxActivity} opens a brand new session. */
    public static final String EXTRA_NEW_SESSION = "com.termux.app.extra.new_session";

    private TerminalCommands() { }

    /** Open the terminal and run {@code command} in the current session. */
    public static void run(@NonNull Context context, @NonNull String command) {
        Intent intent = new Intent(context, TermuxActivity.class);
        intent.putExtra(EXTRA_RUN_COMMAND, command);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /** Open a brand new session and run {@code command} in it. */
    public static void runInNewSession(@NonNull Context context, @NonNull String command) {
        Intent intent = new Intent(context, TermuxActivity.class);
        intent.putExtra(EXTRA_NEW_SESSION, true);
        intent.putExtra(EXTRA_RUN_COMMAND, command);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static void openTerminal(@NonNull Context context) {
        Intent intent = new Intent(context, TermuxActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static void openSession(@NonNull Context context, @NonNull String sessionHandle) {
        Intent intent = new Intent(context, TermuxActivity.class);
        intent.putExtra(EXTRA_SWITCH_SESSION, sessionHandle);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /** Single-quote a string for a POSIX shell. */
    @NonNull
    public static String quote(@NonNull String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
