package com.termux.app.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Ready-made and custom shell command templates. */
public class CommandTemplatesActivity extends RedesignActivity {

    private static final Pattern PLACEHOLDER = Pattern.compile("<([^<>\\n]{1,40})>");

    private String mCategory = null; // null == All
    private String mQuery = "";
    private final List<TemplateStore.Template> mVisible = new ArrayList<>();
    private RecyclerView.Adapter<Holder> mAdapter;
    private TextView mChipAll, mChipDev, mChipSys, mChipNet;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_templates);
        setupHeader("Command Templates", query -> {
            mQuery = query.toLowerCase(Locale.ROOT);
            refresh();
        });

        mChipAll = findViewById(R.id.chip_all);
        mChipDev = findViewById(R.id.chip_development);
        mChipSys = findViewById(R.id.chip_system);
        mChipNet = findViewById(R.id.chip_network);
        mChipAll.setOnClickListener(v -> selectCategory(null));
        mChipDev.setOnClickListener(v -> selectCategory(TemplateStore.CAT_DEVELOPMENT));
        mChipSys.setOnClickListener(v -> selectCategory(TemplateStore.CAT_SYSTEM));
        mChipNet.setOnClickListener(v -> selectCategory(TemplateStore.CAT_NETWORK));

        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new RecyclerView.Adapter<Holder>() {
            @NonNull @Override
            public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_template, parent, false));
            }
            @Override public void onBindViewHolder(@NonNull Holder h, int position) { bind(h, mVisible.get(position)); }
            @Override public int getItemCount() { return mVisible.size(); }
        };
        list.setAdapter(mAdapter);

        findViewById(R.id.btn_new_template).setOnClickListener(v -> showNewTemplateDialog());
        selectCategory(null);
    }

    private void selectCategory(String category) {
        mCategory = category;
        mChipAll.setSelected(category == null);
        mChipDev.setSelected(TemplateStore.CAT_DEVELOPMENT.equals(category));
        mChipSys.setSelected(TemplateStore.CAT_SYSTEM.equals(category));
        mChipNet.setSelected(TemplateStore.CAT_NETWORK.equals(category));
        refresh();
    }

    private void refresh() {
        List<TemplateStore.Template> all = new ArrayList<>(TemplateStore.builtIns());
        all.addAll(TemplateStore.custom(this));
        mVisible.clear();
        for (TemplateStore.Template t : all) {
            if (mCategory != null && !mCategory.equals(t.category)) continue;
            if (!mQuery.isEmpty() && !t.name.toLowerCase(Locale.ROOT).contains(mQuery)
                && !t.description.toLowerCase(Locale.ROOT).contains(mQuery)) continue;
            mVisible.add(t);
        }
        mAdapter.notifyDataSetChanged();
    }

    private void bind(Holder h, TemplateStore.Template t) {
        h.name.setText(t.name);
        h.desc.setText(t.description);
        h.icon.setImageResource(Icons.byName(t.icon));
        h.icon.setColorFilter(ContextCompat.getColor(this, R.color.tx_text));
        h.itemView.setOnClickListener(v -> onTemplateClicked(t));
        h.itemView.setOnLongClickListener(v -> {
            if (t.builtIn) return false;
            UiPrefs.show(this, new AlertDialog.Builder(this)
                .setTitle("Delete \"" + t.name + "\"?")
                .setPositiveButton("Delete", (d, w) -> {
                    TemplateStore.removeCustom(this, t.name, t.commands.get(0).command);
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null));
            return true;
        });
    }

    private void onTemplateClicked(TemplateStore.Template t) {
        if (t.commands.size() == 1) {
            runWithParams(t.commands.get(0).command);
            return;
        }
        String[] labels = new String[t.commands.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = t.commands.get(i).label;
        UiPrefs.show(this, new AlertDialog.Builder(this)
            .setTitle(t.name)
            .setItems(labels, (d, which) -> runWithParams(t.commands.get(which).command))
            .setNegativeButton(android.R.string.cancel, null));
    }

    /** Ask for any {@code <placeholder>} values, then run the command in the terminal. */
    private void runWithParams(String command) {
        Set<String> names = new LinkedHashSet<>();
        Matcher m = PLACEHOLDER.matcher(command);
        while (m.find()) names.add(m.group(1));
        if (names.isEmpty()) {
            TerminalCommands.run(this, command);
            return;
        }
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), 0);
        List<String> keys = new ArrayList<>(names);
        List<EditText> fields = new ArrayList<>();
        for (String key : keys) {
            EditText et = new EditText(this);
            et.setHint(key);
            et.setSingleLine(true);
            form.addView(et);
            fields.add(et);
        }
        UiPrefs.show(this, new AlertDialog.Builder(this)
            .setTitle("Fill in values")
            .setMessage(command)
            .setView(form)
            .setPositiveButton("Run", (d, w) -> {
                String result = command;
                for (int i = 0; i < keys.size(); i++) {
                    String value = fields.get(i).getText().toString().trim();
                    result = result.replace("<" + keys.get(i) + ">", value.isEmpty() ? keys.get(i) : value);
                }
                TerminalCommands.run(this, result);
            })
            .setNegativeButton(android.R.string.cancel, null));
    }

    private void showNewTemplateDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), 0);

        EditText name = new EditText(this);
        name.setHint("Name");
        name.setSingleLine(true);
        EditText desc = new EditText(this);
        desc.setHint("Description");
        desc.setSingleLine(true);
        EditText cmd = new EditText(this);
        cmd.setHint("Command (use <value> for inputs)");
        cmd.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        Spinner category = new Spinner(this);
        category.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
            new String[]{TemplateStore.CAT_DEVELOPMENT, TemplateStore.CAT_SYSTEM, TemplateStore.CAT_NETWORK}));
        form.addView(name);
        form.addView(desc);
        form.addView(cmd);
        form.addView(category);

        UiPrefs.show(this, new AlertDialog.Builder(this)
            .setTitle("New template")
            .setView(form)
            .setPositiveButton("Save", (d, w) -> {
                String n = name.getText().toString().trim();
                String c = cmd.getText().toString().trim();
                if (n.isEmpty() || c.isEmpty()) return;
                TemplateStore.addCustom(this, n, desc.getText().toString().trim(), (String) category.getSelectedItem(), c);
                refresh();
            })
            .setNegativeButton(android.R.string.cancel, null));
    }

    private static final class Holder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView name, desc;

        Holder(View v) {
            super(v);
            icon = v.findViewById(R.id.tpl_icon);
            name = v.findViewById(R.id.tpl_name);
            desc = v.findViewById(R.id.tpl_desc);
        }
    }
}
