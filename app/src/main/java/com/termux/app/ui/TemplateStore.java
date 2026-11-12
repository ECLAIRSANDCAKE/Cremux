package com.termux.app.ui;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Built-in and user created command templates. */
public final class TemplateStore {

    public static final String CAT_DEVELOPMENT = "Development";
    public static final String CAT_SYSTEM = "System";
    public static final String CAT_NETWORK = "Network";

    public static final class Cmd {
        public final String label;
        public final String command;
        Cmd(String label, String command) { this.label = label; this.command = command; }
    }

    public static final class Template {
        public final String name;
        public final String description;
        public final String category;
        public final String icon;
        public final boolean builtIn;
        public final List<Cmd> commands = new ArrayList<>();

        Template(String name, String description, String category, String icon, boolean builtIn) {
            this.name = name;
            this.description = description;
            this.category = category;
            this.icon = icon;
            this.builtIn = builtIn;
        }

        Template add(String label, String command) {
            commands.add(new Cmd(label, command));
            return this;
        }
    }

    private static final String PREFS = "redesign_templates";
    private static final String KEY = "custom";

    private TemplateStore() { }

    @NonNull
    public static List<Template> builtIns() {
        List<Template> t = new ArrayList<>();
        t.add(new Template("Python", "Run Python or pip", CAT_DEVELOPMENT, "code", true)
            .add("Install Python", "pkg install -y python")
            .add("Python version", "python --version")
            .add("Start interpreter", "python")
            .add("Run a script", "python <script.py>")
            .add("List pip packages", "pip list")
            .add("Install a pip package", "pip install <package>")
            .add("Create virtualenv", "python -m venv <venv-name>"));
        t.add(new Template("Git", "Manage repositories", CAT_DEVELOPMENT, "branch", true)
            .add("Install Git", "pkg install -y git")
            .add("Status", "git status")
            .add("Recent commits", "git log --oneline -15")
            .add("Pull", "git pull")
            .add("Clone a repository", "git clone <url>")
            .add("Set user name", "git config --global user.name \"<your name>\"")
            .add("Set user email", "git config --global user.email \"<you@example.com>\""));
        t.add(new Template("Docker", "Container management", CAT_DEVELOPMENT, "container", true)
            .add("Install Docker", "pkg install -y docker")
            .add("Version", "docker --version")
            .add("Running containers", "docker ps")
            .add("All containers", "docker ps -a")
            .add("Images", "docker images"));
        t.add(new Template("Network Tools", "Ping, traceroute, etc.", CAT_NETWORK, "globe", true)
            .add("Ping a host", "ping -c 4 <host>")
            .add("Traceroute", "traceroute <host>")
            .add("Install network tools", "pkg install -y traceroute dnsutils nmap")
            .add("DNS lookup", "nslookup <host>")
            .add("Public IP", "curl -s ifconfig.me; echo")
            .add("Network interfaces", "ip addr"));
        t.add(new Template("System Info", "View system information", CAT_SYSTEM, "info", true)
            .add("Kernel and architecture", "uname -a")
            .add("Neofetch", "pkg install -y neofetch && neofetch")
            .add("Disk usage", "df -h")
            .add("Memory", "free -h")
            .add("Uptime", "uptime")
            .add("Environment", "env | sort"));
        return t;
    }

    @NonNull
    public static List<Template> custom(@NonNull Context context) {
        List<Template> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(context).getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Template t = new Template(o.getString("name"), o.optString("description"),
                    o.optString("category", CAT_DEVELOPMENT), "prompt", false);
                t.add(o.getString("name"), o.getString("command"));
                out.add(t);
            }
        } catch (JSONException ignored) { }
        return out;
    }

    public static void addCustom(@NonNull Context context, String name, String description, String category, String command) {
        try {
            JSONArray arr = new JSONArray(prefs(context).getString(KEY, "[]"));
            JSONObject o = new JSONObject();
            o.put("name", name);
            o.put("description", description);
            o.put("category", category);
            o.put("command", command);
            arr.put(o);
            prefs(context).edit().putString(KEY, arr.toString()).apply();
        } catch (JSONException ignored) { }
    }

    public static void removeCustom(@NonNull Context context, String name, String command) {
        try {
            JSONArray arr = new JSONArray(prefs(context).getString(KEY, "[]"));
            JSONArray out = new JSONArray();
            boolean removed = false;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (!removed && name.equals(o.optString("name")) && command.equals(o.optString("command"))) {
                    removed = true;
                    continue;
                }
                out.put(o);
            }
            prefs(context).edit().putString(KEY, out.toString()).apply();
        } catch (JSONException ignored) { }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
