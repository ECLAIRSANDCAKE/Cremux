package com.termux.app.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.termux.terminal.TerminalSession;

/** Shared "name it, then save it" dialog used by the terminal menu and Home. */
public final class SessionSaver {

    private SessionSaver() { }

    public static void promptAndSave(Context ctx, @Nullable TerminalSession session) {
        if (session == null) {
            Toast.makeText(ctx, "No session to save", Toast.LENGTH_SHORT).show();
            return;
        }
        final EditText input = new EditText(ctx);
        input.setSingleLine(true);
        input.setHint("Name");
        String suggested = session.mSessionName;
        input.setText(suggested == null || suggested.isEmpty() ? "Session" : suggested);
        FrameLayout wrap = new FrameLayout(ctx);
        int pad = (int) (20 * ctx.getResources().getDisplayMetrics().density);
        wrap.setPadding(pad, pad / 2, pad, 0);
        wrap.addView(input, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        UiPrefs.show(ctx, new AlertDialog.Builder(ctx)
            .setTitle("Save session")
            .setMessage("Saves the output and the current folder. Restore it any time from Home.")
            .setView(wrap)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("Save", (d, w) -> {
                String name = input.getText().toString().trim();
                if (name.isEmpty()) name = "Session";
                try {
                    SessionArchive.save(session, name);
                    Toast.makeText(ctx, "Saved \"" + name + "\"", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(ctx, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }));
    }
}
