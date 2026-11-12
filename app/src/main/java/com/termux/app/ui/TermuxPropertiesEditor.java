package com.termux.app.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.TermuxConstants;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Reads and updates single keys of {@code ~/.termux/termux.properties}. */
public final class TermuxPropertiesEditor {

    /** Set when a value changed; {@code TermuxActivity} reloads its styling on the next start. */
    public static volatile boolean sDirty = false;

    private TermuxPropertiesEditor() { }

    @NonNull
    private static File targetFile() {
        for (String path : TermuxConstants.TERMUX_PROPERTIES_FILE_PATHS_LIST) {
            File f = new File(path);
            if (f.isFile()) return f;
        }
        return new File(TermuxConstants.TERMUX_PROPERTIES_PRIMARY_FILE_PATH);
    }

    @Nullable
    public static synchronized String get(@NonNull String key) {
        File file = targetFile();
        if (!file.isFile()) return null;
        String result = null;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String t = line.trim();
                if (t.startsWith("#")) continue;
                int eq = t.indexOf('=');
                if (eq > 0 && t.substring(0, eq).trim().equals(key)) result = t.substring(eq + 1).trim();
            }
        } catch (IOException ignored) { }
        return result;
    }

    public static synchronized boolean set(@NonNull String key, @NonNull String value) {
        File file = targetFile();
        List<String> lines = new ArrayList<>();
        if (file.isFile()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) lines.add(line);
            } catch (IOException e) {
                return false;
            }
        }
        boolean replaced = false;
        for (int i = 0; i < lines.size(); i++) {
            String t = lines.get(i).trim();
            if (t.startsWith("#")) continue;
            int eq = t.indexOf('=');
            if (eq > 0 && t.substring(0, eq).trim().equals(key)) {
                lines.set(i, key + "=" + value);
                replaced = true;
            }
        }
        if (!replaced) lines.add(key + "=" + value);

        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) return false;
        try (FileWriter writer = new FileWriter(file, false)) {
            for (String line : lines) writer.write(line + "\n");
        } catch (IOException e) {
            return false;
        }
        sDirty = true;
        return true;
    }
}
