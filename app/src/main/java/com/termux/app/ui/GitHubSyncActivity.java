package com.termux.app.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.Base64;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import android.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;

import com.termux.R;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** One-tap "pick files, pick repo, push" screen. Runs real git inside the Termux environment. */
public class GitHubSyncActivity extends RedesignActivity {

    private static final int REQ_ZIP = 4101;
    private static final int REQ_TREE = 4102;

    private TextView mAccount, mSource, mRepoText, mModeHint, mLog, mPush;
    private View mLogCard, mProgress;
    private TextView mModeUpdate, mModeFresh;
    private SwitchCompat mUnwrap, mMirror;
    private EditText mMessage;

    private Uri mSourceUri;
    private boolean mSourceIsZip;
    private GitHubApi.Repo mRepo;
    private boolean mRepoIsNew;
    private boolean mFresh;
    private volatile boolean mRunning;

    private final StringBuilder mCommitted = new StringBuilder();
    private String mCurrentLine = "";
    private long mLastRender;
    private String mSecret1, mSecret2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_github_sync);
        setupHeader("GitHub Sync", null);

        mAccount = findViewById(R.id.tv_account);
        mSource = findViewById(R.id.tv_source);
        mRepoText = findViewById(R.id.tv_repo);
        mModeHint = findViewById(R.id.tv_mode_hint);
        mLog = findViewById(R.id.tv_log);
        mLogCard = findViewById(R.id.log_card);
        mProgress = findViewById(R.id.progress);
        mModeUpdate = findViewById(R.id.mode_update);
        mModeFresh = findViewById(R.id.mode_fresh);
        mUnwrap = findViewById(R.id.sw_unwrap);
        mMirror = findViewById(R.id.sw_mirror);
        mMessage = findViewById(R.id.et_message);
        mPush = findViewById(R.id.btn_push);
        mUnwrap.setChecked(true);

        findViewById(R.id.btn_account).setOnClickListener(v -> showCredentialsDialog(null));
        findViewById(R.id.btn_signout).setOnClickListener(v -> {
            GitHubCredentials.clear(this);
            mRepo = null;
            refreshAll();
            toast("Signed out. Token removed from this device.");
        });
        findViewById(R.id.btn_pick_zip).setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/x-zip-compressed", "application/octet-stream"});
            startActivityForResult(i, REQ_ZIP);
        });
        findViewById(R.id.btn_pick_folder).setOnClickListener(v ->
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQ_TREE));
        findViewById(R.id.btn_repo).setOnClickListener(v -> ensureCredentials(this::loadRepos));
        mModeUpdate.setOnClickListener(v -> setMode(false));
        mModeFresh.setOnClickListener(v -> setMode(true));
        mPush.setOnClickListener(v -> onPushClicked());

        setMode(false);
        refreshAll();
    }

    // ---------- UI state ----------

    private void refreshAll() {
        if (GitHubCredentials.hasCredentials(this)) {
            mAccount.setText("Signed in as " + GitHubCredentials.getUser(this) + " · token stored encrypted");
        } else {
            mAccount.setText("Not connected. Tap Connect to add your username and token.");
        }
        findViewById(R.id.btn_signout).setVisibility(GitHubCredentials.hasCredentials(this) ? View.VISIBLE : View.GONE);
        mRepoText.setText(mRepo == null ? "No repository chosen"
            : mRepo.fullName + (mRepo.isPrivate ? " · private" : " · public") + (mRepoIsNew ? " · new" : ""));
    }

    private void setMode(boolean fresh) {
        mFresh = fresh;
        mModeUpdate.setSelected(!fresh);
        mModeFresh.setSelected(fresh);
        mMirror.setVisibility(fresh ? View.GONE : View.VISIBLE);
        mModeHint.setText(fresh
            ? "Starts a brand new history from your files and force-pushes it, replacing what the repo has."
            : "Adds/overwrites files on top of what is already in the repo.");
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private void showError(String title, String msg) {
        UiPrefs.show(this, new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("OK", null));
    }

    // ---------- Source ----------

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        mSourceUri = data.getData();
        mSourceIsZip = requestCode == REQ_ZIP;
        if (requestCode == REQ_TREE) {
            try {
                getContentResolver().takePersistableUriPermission(mSourceUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) { }
        }
        mSource.setText((mSourceIsZip ? "Zip: " : "Folder: ") + SourceStager.displayName(this, mSourceUri));
    }

    // ---------- Credentials ----------

    private void ensureCredentials(Runnable next) {
        if (GitHubCredentials.hasCredentials(this)) next.run();
        else showCredentialsDialog(next);
    }

    private void showCredentialsDialog(Runnable then) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);

        TextView hint = new TextView(this);
        hint.setText("Create a Personal Access Token at github.com/settings/tokens with the \"repo\" scope. "
            + "It is stored encrypted with the Android Keystore and only sent to github.com.");
        hint.setTextSize(12);
        box.addView(hint);

        final EditText user = new EditText(this);
        user.setHint("GitHub username (optional, detected from token)");
        user.setSingleLine(true);
        String saved = GitHubCredentials.getUser(this);
        if (saved != null) user.setText(saved);
        box.addView(user);

        final EditText token = new EditText(this);
        token.setHint("Personal access token");
        token.setSingleLine(true);
        token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        box.addView(token);

        AlertDialog.Builder b = new AlertDialog.Builder(this)
            .setTitle("GitHub account")
            .setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Verify & save", (d, w) -> {
                String t = token.getText().toString().trim();
                if (t.isEmpty()) {
                    toast("Token is required");
                    return;
                }
                verifyAndSave(user.getText().toString().trim(), t, then);
            });
        UiPrefs.show(this, b);
    }

    private void verifyAndSave(String typedUser, String token, Runnable then) {
        toast("Checking token…");
        new Thread(() -> {
            try {
                String login = GitHubApi.getLogin(token);
                boolean persisted = GitHubCredentials.save(this, login, token);
                runOnUiThread(() -> {
                    refreshAll();
                    if (!typedUser.isEmpty() && !typedUser.equalsIgnoreCase(login)) {
                        toast("Token belongs to " + login + ", using that.");
                    } else if (!persisted) {
                        toast("Saved for this session only (Keystore unavailable).");
                    } else {
                        toast("Connected as " + login);
                    }
                    if (then != null) then.run();
                });
            } catch (Exception e) {
                runOnUiThread(() -> showError("Could not verify token", String.valueOf(e.getMessage())));
            }
        }).start();
    }

    // ---------- Repo ----------

    private void loadRepos() {
        final String token = GitHubCredentials.getToken(this);
        if (token == null) return;
        toast("Loading your repositories…");
        new Thread(() -> {
            try {
                List<GitHubApi.Repo> repos = GitHubApi.listRepos(token);
                runOnUiThread(() -> showRepoPicker(repos));
            } catch (Exception e) {
                runOnUiThread(() -> showError("Could not load repositories", String.valueOf(e.getMessage())));
            }
        }).start();
    }

    private void showRepoPicker(List<GitHubApi.Repo> repos) {
        List<String> labels = new ArrayList<>();
        labels.add("＋  Create new repository");
        for (GitHubApi.Repo r : repos) labels.add(r.fullName + (r.isPrivate ? "  🔒" : ""));
        AlertDialog.Builder b = new AlertDialog.Builder(this)
            .setTitle("Choose repository")
            .setItems(labels.toArray(new String[0]), (d, which) -> {
                if (which == 0) {
                    showCreateRepoDialog();
                } else {
                    mRepo = repos.get(which - 1);
                    mRepoIsNew = false;
                    refreshAll();
                }
            })
            .setNegativeButton("Cancel", null);
        UiPrefs.show(this, b);
    }

    private void showCreateRepoDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        final EditText name = new EditText(this);
        name.setHint("Repository name");
        name.setSingleLine(true);
        box.addView(name);
        final CheckBox priv = new CheckBox(this);
        priv.setText("Private repository");
        priv.setChecked(true);
        box.addView(priv);

        AlertDialog.Builder b = new AlertDialog.Builder(this)
            .setTitle("New repository")
            .setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Create", (d, w) -> {
                String n = name.getText().toString().trim().replace(' ', '-');
                if (n.isEmpty()) {
                    toast("Name is required");
                    return;
                }
                createRepo(n, priv.isChecked());
            });
        UiPrefs.show(this, b);
    }

    private void createRepo(String name, boolean isPrivate) {
        final String token = GitHubCredentials.getToken(this);
        if (token == null) return;
        toast("Creating " + name + "…");
        new Thread(() -> {
            try {
                GitHubApi.Repo r = GitHubApi.createRepo(token, name, isPrivate);
                runOnUiThread(() -> {
                    mRepo = r;
                    mRepoIsNew = true;
                    setMode(true);
                    refreshAll();
                    toast("Created " + r.fullName);
                });
            } catch (Exception e) {
                runOnUiThread(() -> showError("Could not create repository", String.valueOf(e.getMessage())));
            }
        }).start();
    }

    // ---------- Push ----------

    private void onPushClicked() {
        if (mRunning) return;
        if (mSourceUri == null) {
            toast("Pick a zip or folder first");
            return;
        }
        if (!GitHubCredentials.hasCredentials(this)) {
            showCredentialsDialog(this::onPushClicked);
            return;
        }
        if (mRepo == null) {
            toast("Choose or create a repository first");
            return;
        }
        if (mFresh && !mRepoIsNew) {
            UiPrefs.show(this, new AlertDialog.Builder(this)
                .setTitle("Replace repository content?")
                .setMessage("Fresh files force-pushes a new history to " + mRepo.fullName + " (" + mRepo.defaultBranch
                    + "). Existing commits on that branch will be overwritten.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Push", (d, w) -> startPush()));
        } else {
            startPush();
        }
    }

    private void startPush() {
        final String user = GitHubCredentials.getUser(this);
        final String token = GitHubCredentials.getToken(this);
        if (user == null || token == null) return;
        final GitHubApi.Repo repo = mRepo;
        final Uri uri = mSourceUri;
        final boolean isZip = mSourceIsZip;
        final boolean unwrap = mUnwrap.isChecked();
        final boolean fresh = mFresh;
        final boolean mirror = mMirror.isChecked();
        String m = mMessage.getText().toString().trim();
        final String message = m.isEmpty() ? "Update from Crémux" : m;

        mSecret1 = token;
        mSecret2 = Base64.encodeToString((user + ":" + token).getBytes(), Base64.NO_WRAP);
        mRunning = true;
        mPush.setEnabled(false);
        mPush.setAlpha(0.5f);
        mCommitted.setLength(0);
        mCurrentLine = "";
        mLogCard.setVisibility(View.VISIBLE);
        mProgress.setVisibility(View.VISIBLE);
        render(true);
        scrollToLog();

        new Thread(() -> {
            File base = new File(TermuxConstants.TERMUX_HOME_DIR_PATH, ".cremux_sync");
            int code = -1;
            try {
                File bash = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "bash");
                if (!bash.canExecute()) {
                    throw new IOException("Termux environment is not installed yet. Open Terminal once and let it finish setup.");
                }
                File src = new File(base, "src");
                File work = new File(base, "work");
                line("==> Reading " + (isZip ? "zip" : "folder") + " from your phone");
                int n = isZip ? SourceStager.stageZip(this, uri, src, unwrap) : SourceStager.stageTree(this, uri, src);
                line("Staged " + n + " files");

                String script = readRaw(R.raw.cremux_push);
                ProcessBuilder pb = new ProcessBuilder(bash.getPath(), "-c", script, "cremux",
                    src.getPath(), work.getPath(), repo.fullName,
                    fresh && !mRepoIsNew ? repo.defaultBranch : (fresh ? "main" : repo.defaultBranch),
                    fresh ? "fresh" : "update", message, mirror ? "1" : "0",
                    user, user + "@users.noreply.github.com");
                HashMap<String, String> env = new TermuxShellEnvironment().getEnvironment(this, false);
                pb.environment().clear();
                pb.environment().putAll(env);
                pb.environment().put("GIT_CONFIG_COUNT", "1");
                pb.environment().put("GIT_CONFIG_KEY_0", "http.extraheader");
                pb.environment().put("GIT_CONFIG_VALUE_0", "Authorization: Basic " + mSecret2);
                pb.directory(new File(TermuxConstants.TERMUX_HOME_DIR_PATH));
                pb.redirectErrorStream(true);
                Process p = pb.start();
                p.getOutputStream().close();
                InputStream in = p.getInputStream();
                int c;
                while ((c = in.read()) != -1) feed((char) c);
                flushPartial();
                code = p.waitFor();
            } catch (Exception e) {
                line("ERROR: " + e.getMessage());
            } finally {
                SourceStager.deleteRecursive(new File(base, "work"));
                SourceStager.deleteRecursive(new File(base, "src"));
            }
            final int exit = code;
            runOnUiThread(() -> finishPush(exit, repo));
        }).start();
    }

    private void finishPush(int exit, GitHubApi.Repo repo) {
        mRunning = false;
        mSecret1 = null;
        mSecret2 = null;
        mPush.setEnabled(true);
        mPush.setAlpha(1f);
        mProgress.setVisibility(View.GONE);
        render(true);
        if (exit == 0) {
            UiPrefs.show(this, new AlertDialog.Builder(this)
                .setTitle("Push complete")
                .setMessage(repo.fullName)
                .setNegativeButton("Close", null)
                .setPositiveButton("Open on GitHub", (d, w) ->
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/" + repo.fullName)))));
            mRepoIsNew = false;
            refreshAll();
        } else {
            toast("Push failed. See the log below.");
        }
    }

    // ---------- Log ----------

    private String mask(String s) {
        if (mSecret1 != null && !mSecret1.isEmpty()) s = s.replace(mSecret1, "***");
        if (mSecret2 != null && !mSecret2.isEmpty()) s = s.replace(mSecret2, "***");
        return s;
    }

    private void line(String s) {
        synchronized (mCommitted) {
            mCommitted.append(mask(s)).append('\n');
            mCurrentLine = "";
        }
        render(false);
    }

    /** Git draws progress with carriage returns: '\r' rewrites the current line, '\n' commits it. */
    private void feed(char c) {
        synchronized (mCommitted) {
            if (c == '\n') {
                mCommitted.append(mask(mCurrentLine)).append('\n');
                mCurrentLine = "";
            } else if (c == '\r') {
                mCurrentLine = "";
            } else {
                mCurrentLine += c;
            }
            if (mCommitted.length() > 60000) mCommitted.delete(0, 20000);
        }
        render(false);
    }

    private void flushPartial() {
        synchronized (mCommitted) {
            if (!mCurrentLine.isEmpty()) {
                mCommitted.append(mask(mCurrentLine)).append('\n');
                mCurrentLine = "";
            }
        }
        render(true);
    }

    private void render(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - mLastRender < 120) return;
        mLastRender = now;
        final String text;
        synchronized (mCommitted) {
            text = mCommitted.toString() + mask(mCurrentLine);
        }
        runOnUiThread(() -> mLog.setText(text));
    }

    private void scrollToLog() {
        final ScrollView sv = findViewById(R.id.log_card).getParent() instanceof View
            ? findScroll((View) findViewById(R.id.log_card).getParent()) : null;
        if (sv != null) sv.post(() -> sv.fullScroll(View.FOCUS_DOWN));
    }

    private ScrollView findScroll(View v) {
        while (v != null) {
            if (v instanceof ScrollView) return (ScrollView) v;
            v = v.getParent() instanceof View ? (View) v.getParent() : null;
        }
        return null;
    }

    private String readRaw(int id) throws IOException {
        InputStream in = getResources().openRawResource(id);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        return bos.toString("UTF-8");
    }
}
