package com.termux.app.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.annotation.Nullable;

import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * GitHub username + token storage. The token is encrypted with an AES-256-GCM key that lives in the
 * Android Keystore (API 23+). On older devices the token is kept in memory only (never written to disk).
 */
public final class GitHubCredentials {

    private static final String FILE = "cremux_github";
    private static final String ALIAS = "cremux_github_token_key";
    private static final String KEY_USER = "user";
    private static final String KEY_TOKEN = "token_enc";

    private static String sMemoryToken;

    private GitHubCredentials() { }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    @Nullable
    public static String getUser(Context c) {
        return prefs(c).getString(KEY_USER, null);
    }

    @Nullable
    public static String getToken(Context c) {
        if (Build.VERSION.SDK_INT < 23) return sMemoryToken;
        String stored = prefs(c).getString(KEY_TOKEN, null);
        if (stored == null) return null;
        try {
            byte[] all = Base64.decode(stored, Base64.NO_WRAP);
            byte[] iv = new byte[12];
            System.arraycopy(all, 0, iv, 0, 12);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(all, 12, all.length - 12), "UTF-8");
        } catch (Exception e) {
            // Key was invalidated or data corrupt: treat as not stored.
            return null;
        }
    }

    public static boolean hasCredentials(Context c) {
        String u = getUser(c);
        String t = getToken(c);
        return u != null && !u.isEmpty() && t != null && !t.isEmpty();
    }

    /** @return true if the token was persisted (false = kept in memory only for this app run). */
    public static boolean save(Context c, String user, String token) {
        SharedPreferences.Editor ed = prefs(c).edit().putString(KEY_USER, user);
        if (Build.VERSION.SDK_INT < 23) {
            sMemoryToken = token;
            ed.remove(KEY_TOKEN).apply();
            return false;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = cipher.getIV();
            byte[] ct = cipher.doFinal(token.getBytes("UTF-8"));
            byte[] all = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, all, 0, iv.length);
            System.arraycopy(ct, 0, all, iv.length, ct.length);
            ed.putString(KEY_TOKEN, Base64.encodeToString(all, Base64.NO_WRAP)).apply();
            return true;
        } catch (Exception e) {
            sMemoryToken = token;
            ed.remove(KEY_TOKEN).apply();
            return false;
        }
    }

    public static void setLastPush(Context c, String repo) {
        prefs(c).edit().putString("last_repo", repo).putLong("last_time", System.currentTimeMillis()).apply();
    }

    @Nullable
    public static String getLastRepo(Context c) {
        return prefs(c).getString("last_repo", null);
    }

    public static long getLastTime(Context c) {
        return prefs(c).getLong("last_time", 0);
    }

    public static void clear(Context c) {
        sMemoryToken = null;
        prefs(c).edit().remove(KEY_USER).remove(KEY_TOKEN).apply();
    }

    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (!ks.containsAlias(ALIAS)) {
            KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            kg.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
            return kg.generateKey();
        }
        return (SecretKey) ks.getKey(ALIAS, null);
    }
}
