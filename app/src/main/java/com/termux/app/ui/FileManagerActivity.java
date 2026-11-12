package com.termux.app.ui;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;
import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * File browser with a Linux-style virtual root ("/bin", "/home", "/sdcard", ...) that maps onto
 * the real Termux directories.
 */
public class FileManagerActivity extends RedesignActivity {

    private static final class Entry {
        String name;
        File file;       // null for virtual root entries
        boolean dir;
        long size;
        long modified;
    }

    private static final Map<String, File> ROOTS = new LinkedHashMap<>();

    // Shared between instances so "Paste" works after navigating.
    private static final List<File> sClipboard = new ArrayList<>();
    private static boolean sClipboardCut = false;

    private final List<String> mPath = new ArrayList<>();
    private final List<Entry> mAll = new ArrayList<>();
    private final List<Entry> mShown = new ArrayList<>();
    private final Set<File> mSelected = new HashSet<>();
    private boolean mSelectionMode = false;
    private boolean mShowHidden = false;
    private String mQuery = "";
    private boolean mReadFailed = false;

    private RecyclerView.Adapter<Holder> mAdapter;
    private TextView mEmpty, mPasteLabel, mSelectLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initRoots();
        setContentView(R.layout.activity_files);
        setupHeader("File Manager", query -> {
            mQuery = query.toLowerCase(Locale.ROOT);
            applyFilter();
        });

        mEmpty = findViewById(R.id.empty_view);
        mPasteLabel = findViewById(R.id.act_paste_label);
        mSelectLabel = findViewById(R.id.act_select_label);

        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new RecyclerView.Adapter<Holder>() {
            @NonNull @Override
            public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file, parent, false));
            }
            @Override public void onBindViewHolder(@NonNull Holder h, int position) { bind(h, mShown.get(position)); }
            @Override public int getItemCount() { return mShown.size(); }
        };
        list.setAdapter(mAdapter);

        findViewById(R.id.act_new).setOnClickListener(v -> showNewDialog());
        findViewById(R.id.act_paste).setOnClickListener(v -> paste());
        findViewById(R.id.act_select).setOnClickListener(v -> setSelectionMode(!mSelectionMode));
        findViewById(R.id.act_more).setOnClickListener(this::showMoreMenu);

        String start = getIntent().getStringExtra("path");
        if (start != null) navigateToRealPath(start);
        load();
    }

    private static void initRoots() {
        if (!ROOTS.isEmpty()) return;
        String prefix = TermuxConstants.TERMUX_PREFIX_DIR_PATH;
        ROOTS.put("bin", new File(prefix, "bin"));
        ROOTS.put("dev", new File("/dev"));
        ROOTS.put("etc", new File(prefix, "etc"));
        ROOTS.put("home", new File(TermuxConstants.TERMUX_HOME_DIR_PATH));
        ROOTS.put("sdcard", Environment.getExternalStorageDirectory());
        ROOTS.put("storage", new File(TermuxConstants.TERMUX_STORAGE_HOME_DIR_PATH));
        ROOTS.put("usr", new File(prefix));
        ROOTS.put("var", new File(prefix, "var"));
    }

    private void navigateToRealPath(String real) {
        for (Map.Entry<String, File> r : ROOTS.entrySet()) {
            String base = r.getValue().getAbsolutePath();
            if (real.equals(base) || real.startsWith(base + "/")) {
                mPath.clear();
                mPath.add(r.getKey());
                String rest = real.substring(base.length());
                for (String seg : rest.split("/")) if (!seg.isEmpty()) mPath.add(seg);
                return;
            }
        }
    }

    /** Resolve the current virtual path to a real directory, or {@code null} at the virtual root. */
    private File currentDir() {
        if (mPath.isEmpty()) return null;
        File f = ROOTS.get(mPath.get(0));
        if (f == null) return null;
        for (int i = 1; i < mPath.size(); i++) f = new File(f, mPath.get(i));
        return f;
    }

    private void load() {
        final File dir = currentDir();
        new Thread(() -> {
            List<Entry> out = new ArrayList<>();
            boolean failed = false;
            if (dir == null) {
                for (Map.Entry<String, File> r : ROOTS.entrySet()) {
                    Entry e = new Entry();
                    e.name = r.getKey();
                    e.dir = true;
                    out.add(e);
                }
            } else {
                File[] children = dir.listFiles();
                if (children == null) {
                    failed = true;
                } else {
                    for (File c : children) {
                        Entry e = new Entry();
                        e.name = c.getName();
                        e.file = c;
                        e.dir = c.isDirectory();
                        e.size = c.length();
                        e.modified = c.lastModified();
                        out.add(e);
                    }
                    Collections.sort(out, (a, b) -> {
                        if (a.dir != b.dir) return a.dir ? -1 : 1;
                        return a.name.compareToIgnoreCase(b.name);
                    });
                }
            }
            final boolean fFailed = failed;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                mAll.clear();
                mAll.addAll(out);
                mReadFailed = fFailed;
                mSelected.clear();
                applyFilter();
                renderCrumbs();
            });
        }, "files-load").start();
    }

    private void applyFilter() {
        mShown.clear();
        for (Entry e : mAll) {
            if (!mShowHidden && e.file != null && e.name.startsWith(".")) continue;
            if (!mQuery.isEmpty() && !e.name.toLowerCase(Locale.ROOT).contains(mQuery)) continue;
            mShown.add(e);
        }
        mAdapter.notifyDataSetChanged();
        boolean empty = mShown.isEmpty();
        mEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            mEmpty.setText(mReadFailed
                ? "Cannot read this folder. For /sdcard, grant access with Home → Storage Access."
                : "This folder is empty");
        }
        mPasteLabel.setText(sClipboard.isEmpty() ? "Paste" : "Paste (" + sClipboard.size() + ")");
        mSelectLabel.setText(mSelectionMode ? "Done" : "Select");
    }

    private void renderCrumbs() {
        android.widget.LinearLayout crumbs = findViewById(R.id.crumbs);
        crumbs.removeAllViews();

        ImageView home = new ImageView(this);
        home.setImageResource(R.drawable.ic_home_outline);
        home.setColorFilter(ContextCompat.getColor(this, R.color.tx_accent));
        home.setLayoutParams(new android.widget.LinearLayout.LayoutParams(dp(24), dp(24)));
        home.setOnClickListener(v -> goTo(0));
        crumbs.addView(home);
        crumbs.addView(crumbText("/", -1));
        for (int i = 0; i < mPath.size(); i++) {
            crumbs.addView(crumbText(mPath.get(i), i + 1));
            crumbs.addView(crumbText("/", -1));
        }
        final View scroll = findViewById(R.id.crumb_scroll);
        scroll.post(() -> ((android.widget.HorizontalScrollView) scroll).fullScroll(View.FOCUS_RIGHT));
    }

    private TextView crumbText(String text, int depth) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(15);
        tv.setPadding(dp(4), dp(6), dp(4), dp(6));
        tv.setTextColor(ContextCompat.getColor(this, depth < 0 ? R.color.tx_text_2 : R.color.tx_text));
        if (depth >= 0) tv.setOnClickListener(v -> goTo(depth));
        return tv;
    }

    private void goTo(int depth) {
        while (mPath.size() > depth) mPath.remove(mPath.size() - 1);
        setSelectionMode(false);
        load();
    }

    private void bind(Holder h, Entry e) {
        h.name.setText(e.name);
        boolean selected = e.file != null && mSelected.contains(e.file);
        if (mSelectionMode && e.file != null) {
            h.icon.setImageResource(selected ? R.drawable.ic_check_circle : R.drawable.ic_select);
            h.icon.setColorFilter(ContextCompat.getColor(this, selected ? R.color.tx_accent : R.color.tx_text_2));
        } else {
            h.icon.setImageResource(e.dir ? R.drawable.ic_folder : R.drawable.ic_file);
            h.icon.setColorFilter(ContextCompat.getColor(this, e.dir ? R.color.tx_accent : R.color.tx_text_2));
        }
        if (e.file == null) {
            h.meta.setVisibility(View.GONE);
        } else {
            h.meta.setVisibility(View.VISIBLE);
            String when = DateUtils.formatDateTime(this, e.modified,
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH | DateUtils.FORMAT_SHOW_TIME);
            h.meta.setText(e.dir ? when : Formatter.formatShortFileSize(this, e.size) + "  ·  " + when);
        }
        h.chevron.setVisibility(e.dir && !mSelectionMode ? View.VISIBLE : View.INVISIBLE);
        h.itemView.setOnClickListener(v -> onEntryClicked(e));
        h.itemView.setOnLongClickListener(v -> {
            if (e.file == null) return false;
            if (!mSelectionMode) setSelectionMode(true);
            toggle(e);
            return true;
        });
    }

    private void onEntryClicked(Entry e) {
        if (mSelectionMode && e.file != null) {
            toggle(e);
        } else if (e.dir) {
            mPath.add(e.name);
            mQuery = "";
            load();
        } else {
            showFileActions(e);
        }
    }

    private void toggle(Entry e) {
        if (!mSelected.remove(e.file)) mSelected.add(e.file);
        mAdapter.notifyDataSetChanged();
    }

    private void setSelectionMode(boolean on) {
        mSelectionMode = on;
        if (!on) mSelected.clear();
        mSelectLabel.setText(on ? "Done" : "Select");
        mAdapter.notifyDataSetChanged();
    }

    // ---- Actions ----

    private void showFileActions(Entry e) {
        String[] items = {"Open", "Edit in terminal", "View in terminal", "Copy path", "Rename", "Delete"};
        UiPrefs.show(this, new AlertDialog.Builder(this).setTitle(e.name).setItems(items, (d, which) -> {
            String q = TerminalCommands.quote(e.file.getAbsolutePath());
            switch (which) {
                case 0: TerminalCommands.run(this, "termux-open " + q); break;
                case 1: TerminalCommands.run(this, "${EDITOR:-nano} " + q); break;
                case 2: TerminalCommands.run(this, "less " + q); break;
                case 3: copyPath(e.file); break;
                case 4: rename(e.file); break;
                default: confirmDelete(Collections.singletonList(e.file));
            }
        }));
    }

    private void copyPath(File f) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("path", f.getAbsolutePath()));
        Toast.makeText(this, "Path copied", Toast.LENGTH_SHORT).show();
    }

    private void showNewDialog() {
        File dir = currentDir();
        if (dir == null) {
            Toast.makeText(this, "Open a folder first", Toast.LENGTH_SHORT).show();
            return;
        }
        UiPrefs.show(this, new AlertDialog.Builder(this).setTitle("Create new")
            .setItems(new String[]{"Folder", "File"}, (d, which) -> askName(which == 0 ? "New folder" : "New file", "", name -> {
                File target = new File(dir, name);
                boolean ok;
                try {
                    ok = !target.exists() && (which == 0 ? target.mkdirs() : target.createNewFile());
                } catch (IOException ex) {
                    ok = false;
                }
                toast(ok ? "Created " + name : "Could not create " + name);
                load();
            })));
    }

    private interface NameCallback { void onName(String name); }

    private void askName(String title, String initial, NameCallback cb) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(initial);
        input.setSelectAllOnFocus(true);
        android.widget.FrameLayout wrap = new android.widget.FrameLayout(this);
        wrap.setPadding(dp(20), dp(8), dp(20), 0);
        wrap.addView(input);
        UiPrefs.show(this, new AlertDialog.Builder(this).setTitle(title).setView(wrap)
            .setPositiveButton(android.R.string.ok, (d, w) -> {
                String n = input.getText().toString().trim();
                if (!n.isEmpty() && !n.contains("/")) cb.onName(n);
            })
            .setNegativeButton(android.R.string.cancel, null));
    }

    private void rename(File f) {
        askName("Rename", f.getName(), name -> {
            File target = new File(f.getParentFile(), name);
            toast(!target.exists() && f.renameTo(target) ? "Renamed" : "Rename failed");
            setSelectionMode(false);
            load();
        });
    }

    private void confirmDelete(List<File> files) {
        UiPrefs.show(this, new AlertDialog.Builder(this)
            .setTitle("Delete " + files.size() + (files.size() == 1 ? " item?" : " items?"))
            .setMessage("This cannot be undone.")
            .setPositiveButton("Delete", (d, w) -> runAsync(() -> {
                boolean ok = true;
                for (File f : files) ok &= deleteRecursive(f);
                return ok ? "Deleted" : "Some items could not be deleted";
            }))
            .setNegativeButton(android.R.string.cancel, null));
    }

    private void paste() {
        File dir = currentDir();
        if (dir == null) {
            toast("Open a folder first");
            return;
        }
        if (sClipboard.isEmpty()) {
            toast("Nothing to paste");
            return;
        }
        final List<File> src = new ArrayList<>(sClipboard);
        final boolean cut = sClipboardCut;
        runAsync(() -> {
            boolean ok = true;
            for (File f : src) {
                File target = uniqueTarget(dir, f.getName());
                try {
                    if (f.isDirectory() && target.getAbsolutePath().startsWith(f.getAbsolutePath() + "/")) {
                        ok = false; // cannot copy a folder into itself
                        continue;
                    }
                    if (cut) {
                        if (!f.renameTo(target)) {
                            copyRecursive(f, target);
                            ok &= deleteRecursive(f);
                        }
                    } else {
                        copyRecursive(f, target);
                    }
                } catch (IOException ex) {
                    ok = false;
                }
            }
            if (cut) sClipboard.clear();
            return ok ? "Pasted" : "Some items could not be pasted";
        });
    }

    private void showMoreMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        Menu m = menu.getMenu();
        if (!mSelected.isEmpty()) {
            m.add(0, 1, 0, "Copy");
            m.add(0, 2, 0, "Cut");
            m.add(0, 3, 0, "Delete");
            if (mSelected.size() == 1) m.add(0, 4, 0, "Rename");
        }
        if (mSelectionMode) m.add(0, 5, 0, "Select all");
        m.add(0, 6, 0, "Open terminal here");
        m.add(0, 7, 0, mShowHidden ? "Hide hidden files" : "Show hidden files");
        m.add(0, 8, 0, "Refresh");
        menu.setOnMenuItemClickListener((MenuItem item) -> {
            switch (item.getItemId()) {
                case 1:
                case 2:
                    sClipboard.clear();
                    sClipboard.addAll(mSelected);
                    sClipboardCut = item.getItemId() == 2;
                    toast((sClipboardCut ? "Cut " : "Copied ") + sClipboard.size() + " item(s)");
                    setSelectionMode(false);
                    applyFilter();
                    return true;
                case 3: confirmDelete(new ArrayList<>(mSelected)); return true;
                case 4: rename(mSelected.iterator().next()); return true;
                case 5:
                    for (Entry e : mShown) if (e.file != null) mSelected.add(e.file);
                    mAdapter.notifyDataSetChanged();
                    return true;
                case 6: {
                    File dir = currentDir();
                    if (dir == null) dir = new File(TermuxConstants.TERMUX_HOME_DIR_PATH);
                    TerminalCommands.run(this, "cd " + TerminalCommands.quote(dir.getAbsolutePath()));
                    return true;
                }
                case 7: mShowHidden = !mShowHidden; applyFilter(); return true;
                default: load(); return true;
            }
        });
        menu.show();
    }

    // ---- File operations ----

    private interface Job { String run(); }

    private void runAsync(Job job) {
        new Thread(() -> {
            final String message = job.run();
            runOnUiThread(() -> {
                if (isFinishing()) return;
                toast(message);
                setSelectionMode(false);
                load();
            });
        }, "files-op").start();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private static File uniqueTarget(File dir, String name) {
        File target = new File(dir, name);
        int i = 1;
        while (target.exists()) {
            String base = name;
            String ext = "";
            int dot = name.lastIndexOf('.');
            if (dot > 0) {
                base = name.substring(0, dot);
                ext = name.substring(dot);
            }
            target = new File(dir, base + " copy" + (i > 1 ? " " + i : "") + ext);
            i++;
        }
        return target;
    }

    private static boolean deleteRecursive(File f) {
        boolean ok = true;
        if (f.isDirectory() && !isSymlink(f)) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) ok &= deleteRecursive(k);
        }
        return f.delete() && ok;
    }

    private static boolean isSymlink(File f) {
        try {
            return !f.getCanonicalPath().equals(new File(f.getParentFile().getCanonicalFile(), f.getName()).getPath());
        } catch (IOException e) {
            return false;
        }
    }

    private static void copyRecursive(File src, File dst) throws IOException {
        if (src.isDirectory()) {
            if (!dst.mkdirs() && !dst.isDirectory()) throw new IOException("mkdir failed");
            File[] kids = src.listFiles();
            if (kids != null) for (File k : kids) copyRecursive(k, new File(dst, k.getName()));
        } else {
            try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (isSearchOpen()) {
            closeSearch();
        } else if (mSelectionMode) {
            setSelectionMode(false);
        } else if (!mPath.isEmpty()) {
            mPath.remove(mPath.size() - 1);
            load();
        } else {
            finish();
        }
    }

    private static final class Holder extends RecyclerView.ViewHolder {
        final ImageView icon, chevron;
        final TextView name, meta;

        Holder(View v) {
            super(v);
            icon = v.findViewById(R.id.file_icon);
            chevron = v.findViewById(R.id.file_trailing);
            name = v.findViewById(R.id.file_name);
            meta = v.findViewById(R.id.file_meta);
        }
    }
}
