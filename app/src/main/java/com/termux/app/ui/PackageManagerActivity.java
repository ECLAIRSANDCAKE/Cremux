package com.termux.app.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Search, install, remove and update Termux packages. Actions run in the terminal. */
public class PackageManagerActivity extends RedesignActivity {

    private static final int TAB_SEARCH = 0, TAB_INSTALLED = 1, TAB_UPDATES = 2;

    private PackageRepository.Snapshot mSnapshot = new PackageRepository.Snapshot();
    private int mTab = TAB_SEARCH;
    private String mQuery = "";
    private boolean mLoaded = false;

    private final List<PackageRepository.Pkg> mItems = new ArrayList<>();
    private RecyclerView.Adapter<Holder> mAdapter;
    private TextView mTabSearch, mTabInstalled, mTabUpdates, mNotice, mBarText, mBarButton;
    private EditText mSearchField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_packages);
        setupHeader("Package Manager", query -> mSearchField.setText(query));

        mTabSearch = findViewById(R.id.tab_search);
        mTabInstalled = findViewById(R.id.tab_installed);
        mTabUpdates = findViewById(R.id.tab_updates);
        mNotice = findViewById(R.id.package_notice);
        mBarText = findViewById(R.id.update_bar_text);
        mBarButton = findViewById(R.id.btn_update_all);
        mSearchField = findViewById(R.id.package_search);

        mTabSearch.setOnClickListener(v -> selectTab(TAB_SEARCH));
        mTabInstalled.setOnClickListener(v -> selectTab(TAB_INSTALLED));
        mTabUpdates.setOnClickListener(v -> selectTab(TAB_UPDATES));

        mSearchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                mQuery = s.toString().trim();
                rebuild();
            }
        });

        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new RecyclerView.Adapter<Holder>() {
            @NonNull @Override
            public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_package, parent, false));
            }
            @Override public void onBindViewHolder(@NonNull Holder h, int position) { bind(h, mItems.get(position)); }
            @Override public int getItemCount() { return mItems.size(); }
        };
        list.setAdapter(mAdapter);

        if (getIntent().getBooleanExtra("focus_search", false)) mSearchField.requestFocus();
        selectTab(TAB_SEARCH);
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        new Thread(() -> {
            PackageRepository.Snapshot snap = PackageRepository.load();
            runOnUiThread(() -> {
                if (isFinishing()) return;
                mSnapshot = snap;
                mLoaded = true;
                rebuild();
            });
        }, "pkg-load").start();
    }

    private void selectTab(int tab) {
        mTab = tab;
        mTabSearch.setSelected(tab == TAB_SEARCH);
        mTabInstalled.setSelected(tab == TAB_INSTALLED);
        mTabUpdates.setSelected(tab == TAB_UPDATES);
        rebuild();
    }

    private void rebuild() {
        mItems.clear();
        List<PackageRepository.Pkg> source;
        switch (mTab) {
            case TAB_INSTALLED: source = mSnapshot.installed; break;
            case TAB_UPDATES: source = mSnapshot.updates; break;
            default: source = PackageRepository.search(mSnapshot, mQuery, 150);
        }
        if (mTab == TAB_SEARCH) {
            mItems.addAll(source);
        } else {
            String q = mQuery.toLowerCase(Locale.ROOT);
            for (PackageRepository.Pkg p : source) {
                if (q.isEmpty() || p.name.toLowerCase(Locale.ROOT).contains(q)
                    || p.description.toLowerCase(Locale.ROOT).contains(q)) mItems.add(p);
            }
        }
        mAdapter.notifyDataSetChanged();

        int updates = mSnapshot.updates.size();
        mTabUpdates.setText(updates > 0 ? "Updates  " + updates : "Updates");
        mTabInstalled.setText("Installed");

        if (mLoaded && !mSnapshot.listsPresent) {
            mNotice.setVisibility(View.VISIBLE);
            mNotice.setText("Package lists not found. Tap Refresh to fetch them; popular picks are shown meanwhile.");
        } else if (mLoaded && mTab == TAB_SEARCH && mQuery.isEmpty()) {
            mNotice.setVisibility(View.VISIBLE);
            mNotice.setText("Popular packages — type to search " + mSnapshot.available.size() + " available.");
        } else {
            mNotice.setVisibility(View.GONE);
        }

        if (!mSnapshot.listsPresent) {
            mBarText.setText("No package lists yet");
            mBarButton.setText("Refresh");
            mBarButton.setOnClickListener(v -> TerminalCommands.run(this, "pkg update -y"));
        } else if (updates > 0) {
            mBarText.setText(updates + (updates == 1 ? " package" : " packages") + " available for update");
            mBarButton.setText("Update All");
            mBarButton.setOnClickListener(v -> TerminalCommands.run(this, "pkg upgrade -y"));
        } else {
            mBarText.setText("All packages are up to date");
            mBarButton.setText("Refresh");
            mBarButton.setOnClickListener(v -> TerminalCommands.run(this, "pkg update -y"));
        }
    }

    private void bind(Holder h, PackageRepository.Pkg p) {
        h.name.setText(p.name);
        h.desc.setText(p.description.isEmpty() ? p.version : p.description);
        Icons.PackageIcon icon = Icons.forPackage(p.name);
        h.icon.setImageResource(icon.resId);
        h.icon.setColorFilter(ContextCompat.getColor(this, icon.colorRes));

        final String label;
        if (p.updatable) label = "Update";
        else if (p.installed) label = "Remove";
        else label = "Install";
        h.action.setText(label);
        h.action.setOnClickListener(v -> {
            if (p.updatable) {
                TerminalCommands.run(this, "pkg install -y " + p.name);
            } else if (p.installed) {
                UiPrefs.show(this, new AlertDialog.Builder(this)
                    .setTitle("Remove " + p.name + "?")
                    .setPositiveButton("Remove", (d, w) -> TerminalCommands.run(this, "pkg uninstall -y " + p.name))
                    .setNegativeButton(android.R.string.cancel, null));
            } else {
                TerminalCommands.run(this, "pkg install -y " + p.name);
            }
        });
    }

    private static final class Holder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView name, desc, action;

        Holder(View v) {
            super(v);
            icon = v.findViewById(R.id.pkg_icon);
            name = v.findViewById(R.id.pkg_name);
            desc = v.findViewById(R.id.pkg_desc);
            action = v.findViewById(R.id.pkg_action);
        }
    }
}
