package com.termux.app.ui;

import androidx.annotation.NonNull;

import com.termux.shared.termux.TermuxConstants;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/** Reads the real dpkg database and apt package lists of the Termux environment. */
public final class PackageRepository {

    public static final class Pkg {
        public String name;
        public String version = "";
        public String description = "";
        public boolean installed;
        public boolean updatable;
        public String installedVersion = "";
    }

    public static final class Snapshot {
        public final List<Pkg> available = new ArrayList<>();
        public final List<Pkg> installed = new ArrayList<>();
        public final List<Pkg> updates = new ArrayList<>();
        public boolean listsPresent;
    }

    /** Packages suggested when the search box is empty. */
    public static final String[][] POPULAR = {
        {"python", "Powerful programming language"},
        {"git", "Distributed version control"},
        {"neofetch", "System information tool"},
        {"vim", "Highly configurable editor"},
        {"nano", "Small, friendly text editor"},
        {"curl", "Command line tool for transferring data"},
        {"wget", "Retrieve files from the web"},
        {"openssh", "Secure shell client and server"},
        {"nodejs", "JavaScript runtime"},
        {"clang", "C and C++ compiler"},
    };

    private PackageRepository() { }

    @NonNull
    public static Snapshot load() {
        Snapshot snap = new Snapshot();

        Map<String, Pkg> installed = new HashMap<>();
        File status = new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/var/lib/dpkg/status");
        parse(status, false, (p, st) -> {
            if (st != null && st.contains("install ok installed")) {
                p.installed = true;
                p.installedVersion = p.version;
                installed.put(p.name, p);
            }
        });

        Map<String, Pkg> available = new HashMap<>();
        File listsDir = new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/var/lib/apt/lists");
        File[] files = listsDir.listFiles();
        if (files != null) {
            for (File f : files) {
                String n = f.getName();
                if (!(n.endsWith("_Packages") || n.endsWith("_Packages.gz"))) continue;
                snap.listsPresent = true;
                parse(f, n.endsWith(".gz"), (p, st) -> {
                    Pkg existing = available.get(p.name);
                    if (existing == null || compareVersions(p.version, existing.version) > 0) available.put(p.name, p);
                });
            }
        }

        for (Pkg a : available.values()) {
            Pkg inst = installed.get(a.name);
            if (inst != null) {
                a.installed = true;
                a.installedVersion = inst.installedVersion;
                if (compareVersions(a.version, inst.installedVersion) > 0) {
                    a.updatable = true;
                    inst.updatable = true;
                    snap.updates.add(a);
                }
                if (inst.description.isEmpty()) inst.description = a.description;
            }
            snap.available.add(a);
        }
        snap.installed.addAll(installed.values());

        Comparator c = new Comparator();
        Collections.sort(snap.available, c);
        Collections.sort(snap.installed, c);
        Collections.sort(snap.updates, c);
        return snap;
    }

    private static final class Comparator implements java.util.Comparator<Pkg> {
        @Override public int compare(Pkg a, Pkg b) { return a.name.compareToIgnoreCase(b.name); }
    }

    private interface Visitor { void visit(Pkg pkg, String status); }

    private static void parse(File file, boolean gzip, Visitor visitor) {
        if (!file.isFile()) return;
        try (InputStream raw = new FileInputStream(file);
             InputStream in = gzip ? new GZIPInputStream(raw) : raw;
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"), 64 * 1024)) {
            Pkg cur = null;
            String status = null;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    if (cur != null && cur.name != null) visitor.visit(cur, status);
                    cur = null;
                    status = null;
                    continue;
                }
                char first = line.charAt(0);
                if (first == ' ' || first == '\t') continue;
                if (cur == null) cur = new Pkg();
                if (line.startsWith("Package: ")) cur.name = line.substring(9).trim();
                else if (line.startsWith("Version: ")) cur.version = line.substring(9).trim();
                else if (line.startsWith("Description: ")) cur.description = line.substring(13).trim();
                else if (line.startsWith("Status: ")) status = line.substring(8).trim();
            }
            if (cur != null && cur.name != null) visitor.visit(cur, status);
        } catch (IOException ignored) {
            // Unreadable list: treated as missing.
        }
    }

    /** Filter {@code source} by a user query. Empty query returns the popular picks first. */
    @NonNull
    public static List<Pkg> search(@NonNull Snapshot snap, @NonNull String query, int limit) {
        List<Pkg> out = new ArrayList<>();
        String q = query.toLowerCase(Locale.ROOT);
        Map<String, Pkg> byName = new HashMap<>();
        for (Pkg p : snap.available) byName.put(p.name, p);

        if (q.isEmpty()) {
            for (String[] pop : POPULAR) {
                Pkg p = byName.get(pop[0]);
                if (p == null) {
                    if (snap.listsPresent) continue;
                    p = new Pkg();
                    p.name = pop[0];
                    p.description = pop[1];
                    for (Pkg i : snap.installed) if (i.name.equals(pop[0])) { p.installed = true; p.updatable = i.updatable; }
                } else if (p.description.isEmpty()) {
                    p.description = pop[1];
                }
                out.add(p);
            }
            return out;
        }

        List<Pkg> prefix = new ArrayList<>();
        List<Pkg> contains = new ArrayList<>();
        List<Pkg> inDesc = new ArrayList<>();
        for (Pkg p : snap.available) {
            String n = p.name.toLowerCase(Locale.ROOT);
            if (n.startsWith(q)) prefix.add(p);
            else if (n.contains(q)) contains.add(p);
            else if (p.description.toLowerCase(Locale.ROOT).contains(q)) inDesc.add(p);
        }
        out.addAll(prefix);
        out.addAll(contains);
        out.addAll(inDesc);
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    // ---- Debian version comparison (epoch:upstream-revision) ----

    /** Returns &gt;0 if a is newer than b, &lt;0 if older, 0 if equal. */
    public static int compareVersions(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        int ea = epoch(a), eb = epoch(b);
        if (ea != eb) return ea - eb;
        String ra = stripEpoch(a), rb = stripEpoch(b);
        String ua = upstream(ra), ub = upstream(rb);
        int c = verrevcmp(ua, ub);
        if (c != 0) return c;
        return verrevcmp(revision(ra), revision(rb));
    }

    private static int epoch(String v) {
        int i = v.indexOf(':');
        if (i <= 0) return 0;
        try { return Integer.parseInt(v.substring(0, i)); } catch (NumberFormatException e) { return 0; }
    }

    private static String stripEpoch(String v) {
        int i = v.indexOf(':');
        return i < 0 ? v : v.substring(i + 1);
    }

    private static String upstream(String v) {
        int i = v.lastIndexOf('-');
        return i < 0 ? v : v.substring(0, i);
    }

    private static String revision(String v) {
        int i = v.lastIndexOf('-');
        return i < 0 ? "" : v.substring(i + 1);
    }

    private static int order(char c) {
        if (Character.isDigit(c)) return 0;
        if (Character.isLetter(c)) return c;
        if (c == '~') return -1;
        if (c != 0) return c + 256;
        return 0;
    }

    private static int verrevcmp(String a, String b) {
        int i = 0, j = 0;
        while (i < a.length() || j < b.length()) {
            int firstDiff = 0;
            while ((i < a.length() && !Character.isDigit(a.charAt(i))) || (j < b.length() && !Character.isDigit(b.charAt(j)))) {
                int ac = i < a.length() ? order(a.charAt(i)) : 0;
                int bc = j < b.length() ? order(b.charAt(j)) : 0;
                if (ac != bc) return ac - bc;
                i++;
                j++;
            }
            while (i < a.length() && a.charAt(i) == '0') i++;
            while (j < b.length() && b.charAt(j) == '0') j++;
            while (i < a.length() && Character.isDigit(a.charAt(i)) && j < b.length() && Character.isDigit(b.charAt(j))) {
                if (firstDiff == 0) firstDiff = a.charAt(i) - b.charAt(j);
                i++;
                j++;
            }
            if (i < a.length() && Character.isDigit(a.charAt(i))) return 1;
            if (j < b.length() && Character.isDigit(b.charAt(j))) return -1;
            if (firstDiff != 0) return firstDiff;
        }
        return 0;
    }
}
