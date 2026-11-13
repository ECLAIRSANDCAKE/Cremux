package com.termux.app.ui;

import androidx.annotation.NonNull;

import com.termux.shared.termux.TermuxConstants;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Saves a terminal session (transcript + working directory) under ~/.cremux/sessions. */
public final class SessionArchive {

    public static final class Saved {
        public final String name;
        public final String cwd;
        public final long time;
        public final File transcript;
        final File meta;

        Saved(String name, String cwd, long time, File transcript, File meta) {
            this.name = name;
            this.cwd = cwd;
            this.time = time;
            this.transcript = transcript;
            this.meta = meta;
        }
    }

    private SessionArchive() { }

    public static File dir() {
        return new File(TermuxConstants.TERMUX_HOME_DIR_PATH, ".cremux/sessions");
    }

    public static Saved save(@NonNull TerminalSession session, @NonNull String name) throws Exception {
        TerminalEmulator emulator = session.getEmulator();
        if (emulator == null) throw new IOException("Session has no output yet");
        String text = emulator.getScreen().getTranscriptTextWithFullLinesJoined();
        String cwd = session.getCwd();
        if (cwd == null || cwd.isEmpty()) cwd = TermuxConstants.TERMUX_HOME_DIR_PATH;

        File dir = dir();
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
        long now = System.currentTimeMillis();
        String slug = name.replaceAll("[^A-Za-z0-9._-]+", "_");
        if (slug.isEmpty()) slug = "session";
        File txt = new File(dir, now + "_" + slug + ".txt");
        File meta = new File(dir, now + "_" + slug + ".json");

        writeFile(txt, text);
        JSONObject o = new JSONObject();
        o.put("name", name);
        o.put("cwd", cwd);
        o.put("time", now);
        writeFile(meta, o.toString());
        return new Saved(name, cwd, now, txt, meta);
    }

    public static List<Saved> list() {
        List<Saved> out = new ArrayList<>();
        File[] files = dir().listFiles();
        if (files == null) return out;
        for (File f : files) {
            if (!f.getName().endsWith(".json")) continue;
            try {
                JSONObject o = new JSONObject(readFile(f));
                String base = f.getName().substring(0, f.getName().length() - 5);
                File txt = new File(f.getParentFile(), base + ".txt");
                if (!txt.exists()) continue;
                out.add(new Saved(o.optString("name", base), o.optString("cwd", TermuxConstants.TERMUX_HOME_DIR_PATH),
                    o.optLong("time", f.lastModified()), txt, f));
            } catch (Exception ignored) { }
        }
        Collections.sort(out, (a, b) -> Long.compare(b.time, a.time));
        return out;
    }

    public static void delete(@NonNull Saved s) {
        s.transcript.delete();
        s.meta.delete();
    }

    private static void writeFile(File f, String text) throws IOException {
        FileOutputStream os = new FileOutputStream(f);
        try {
            os.write(text.getBytes("UTF-8"));
        } finally {
            os.close();
        }
    }

    private static String readFile(File f) throws IOException {
        InputStream in = new FileInputStream(f);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        return bos.toString("UTF-8");
    }
}
