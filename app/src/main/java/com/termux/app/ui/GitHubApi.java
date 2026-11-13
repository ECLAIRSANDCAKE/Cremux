package com.termux.app.ui;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** Minimal blocking GitHub REST client. Call from a background thread only. */
public final class GitHubApi {

    public static final class Repo {
        public final String fullName;
        public final String defaultBranch;
        public final boolean isPrivate;

        Repo(String fullName, String defaultBranch, boolean isPrivate) {
            this.fullName = fullName;
            this.defaultBranch = defaultBranch;
            this.isPrivate = isPrivate;
        }
    }

    private GitHubApi() { }

    /** @return the login name the token belongs to; throws if the token is invalid. */
    public static String getLogin(@NonNull String token) throws Exception {
        return new JSONObject(request("GET", "https://api.github.com/user", token, null)).getString("login");
    }

    public static List<Repo> listRepos(@NonNull String token) throws Exception {
        List<Repo> out = new ArrayList<>();
        for (int page = 1; page <= 5; page++) {
            String body = request("GET", "https://api.github.com/user/repos?per_page=100&sort=pushed&affiliation=owner,collaborator&page=" + page, token, null);
            JSONArray arr = new JSONArray(body);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                JSONObject perms = o.optJSONObject("permissions");
                if (perms != null && !perms.optBoolean("push", true)) continue;
                out.add(new Repo(o.getString("full_name"), o.optString("default_branch", "main"), o.optBoolean("private")));
            }
            if (arr.length() < 100) break;
        }
        return out;
    }

    public static Repo createRepo(@NonNull String token, @NonNull String name, boolean isPrivate) throws Exception {
        JSONObject req = new JSONObject();
        req.put("name", name);
        req.put("private", isPrivate);
        req.put("auto_init", false);
        JSONObject o = new JSONObject(request("POST", "https://api.github.com/user/repos", token, req.toString()));
        return new Repo(o.getString("full_name"), o.optString("default_branch", "main"), o.optBoolean("private"));
    }

    private static String request(String method, String url, String token, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setRequestMethod(method);
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestProperty("Authorization", "Bearer " + token);
            c.setRequestProperty("Accept", "application/vnd.github+json");
            c.setRequestProperty("User-Agent", "Cremux");
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                OutputStream os = c.getOutputStream();
                os.write(body.getBytes("UTF-8"));
                os.close();
            }
            int code = c.getResponseCode();
            String text = read(code >= 400 ? c.getErrorStream() : c.getInputStream());
            if (code >= 400) {
                String msg = text;
                try {
                    JSONObject e = new JSONObject(text);
                    msg = e.optString("message", text);
                    JSONArray errs = e.optJSONArray("errors");
                    if (errs != null && errs.length() > 0) {
                        Object first = errs.get(0);
                        String detail = first instanceof JSONObject ? ((JSONObject) first).optString("message", "") : String.valueOf(first);
                        if (!detail.isEmpty()) msg += " (" + detail + ")";
                    }
                } catch (Exception ignored) { }
                if (code == 401) msg = "Token rejected (401). " + msg;
                throw new IOException("GitHub " + code + ": " + msg);
            }
            return text;
        } finally {
            c.disconnect();
        }
    }

    private static String read(InputStream in) throws IOException {
        if (in == null) return "";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        return bos.toString("UTF-8");
    }
}
