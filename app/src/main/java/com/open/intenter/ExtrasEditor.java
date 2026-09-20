package com.open.intenter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Reusable editor for a list of typed extras with unlimited nesting of
 * Bundle entries. Backed by {@link IntentModel.ExtraEntry}.
 */
public class ExtrasEditor {

    public interface OnChange { void onChanged(); }

    static class Row {
        View view;
        TextInputEditText keyInput, valueInput;
        MaterialAutoCompleteTextView typeInput;
        TextInputLayout valueLayout;
        TextView placeholder;
        View childrenBox;
        LinearLayout childrenContainer;
        final List<Row> children = new ArrayList<>();

        String type() {
            CharSequence t = typeInput.getText();
            return t == null || t.length() == 0 ? ExtraTypes.STRING : t.toString();
        }
    }

    private final Context context;
    private final LayoutInflater inflater;
    private final LinearLayout root;
    private final String[] types;
    private final OnChange listener;
    private final List<Row> rows = new ArrayList<>();
    private boolean muted;

    public ExtrasEditor(Context context, LinearLayout root, String[] types, OnChange listener) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.root = root;
        this.types = types;
        this.listener = listener;
    }

    public void add() {
        addRow(root, rows, null);
        changed();
    }

    public void add(IntentModel.ExtraEntry entry) {
        addRow(root, rows, entry);
        changed();
    }

    public void addAll(List<IntentModel.ExtraEntry> entries) {
        muted = true;
        for (IntentModel.ExtraEntry e : entries) addRow(root, rows, e);
        muted = false;
        changed();
    }

    public void set(List<IntentModel.ExtraEntry> entries) {
        muted = true;
        root.removeAllViews();
        rows.clear();
        for (IntentModel.ExtraEntry e : entries) addRow(root, rows, e);
        muted = false;
        changed();
    }

    public void clear() {
        root.removeAllViews();
        rows.clear();
        changed();
    }

    public int count() {
        return count(rows);
    }

    private int count(List<Row> list) {
        int n = 0;
        for (Row r : list) {
            n++;
            n += count(r.children);
        }
        return n;
    }

    public List<IntentModel.ExtraEntry> entries() {
        return entries(rows);
    }

    private List<IntentModel.ExtraEntry> entries(List<Row> list) {
        List<IntentModel.ExtraEntry> out = new ArrayList<>();
        for (Row r : list) {
            IntentModel.ExtraEntry e = new IntentModel.ExtraEntry(
                    UiUtil.text(r.keyInput), UiUtil.raw(r.valueInput), r.type());
            if (ExtraTypes.BUNDLE.equals(e.type)) e.children.addAll(entries(r.children));
            out.add(e);
        }
        return out;
    }

    private void changed() {
        if (!muted && listener != null) listener.onChanged();
    }

    private Row addRow(LinearLayout container, List<Row> list, IntentModel.ExtraEntry initial) {
        View view = inflater.inflate(R.layout.extra_item, container, false);
        Row row = new Row();
        row.view = view;
        row.keyInput = view.findViewById(R.id.extraKeyInput);
        row.valueInput = view.findViewById(R.id.extraValueInput);
        row.typeInput = view.findViewById(R.id.extraTypeSpinner);
        row.valueLayout = view.findViewById(R.id.extraValueLayout);
        row.placeholder = view.findViewById(R.id.extraValuePlaceholder);
        row.childrenBox = view.findViewById(R.id.extraChildren);
        row.childrenContainer = view.findViewById(R.id.extraChildrenContainer);

        String type = initial == null || initial.type == null || !contains(initial.type) ? types[0] : initial.type;
        UiUtil.setupPicker(row.typeInput, types, type);
        row.typeInput.setOnItemClickListener((parent, v, position, id) -> {
            applyType(row, types[position]);
            changed();
        });
        if (initial != null) {
            row.keyInput.setText(initial.key);
            row.valueInput.setText(initial.value);
        }
        applyType(row, type);

        row.keyInput.addTextChangedListener(UiUtil.watcher(this::changed));
        row.valueInput.addTextChangedListener(UiUtil.watcher(this::changed));

        view.findViewById(R.id.removeExtraButton).setOnClickListener(v -> {
            list.remove(row);
            container.removeView(view);
            changed();
        });
        view.findViewById(R.id.addChildExtraButton).setOnClickListener(v -> {
            addRow(row.childrenContainer, row.children, null);
            changed();
        });

        list.add(row);
        container.addView(view);

        if (initial != null && ExtraTypes.BUNDLE.equals(type)) {
            for (IntentModel.ExtraEntry c : initial.children) addRow(row.childrenContainer, row.children, c);
        }
        return row;
    }

    private boolean contains(String type) {
        for (String t : types) if (t.equals(type)) return true;
        return false;
    }

    private void applyType(Row row, String type) {
        boolean container = ExtraTypes.isContainer(type);
        boolean hasValue = ExtraTypes.hasValue(type);
        row.valueLayout.setVisibility(hasValue ? View.VISIBLE : View.GONE);
        row.placeholder.setVisibility(hasValue ? View.GONE : View.VISIBLE);
        row.placeholder.setText(container ? "Nested bundle — add child extras below" : "Null value for this key");
        row.childrenBox.setVisibility(container ? View.VISIBLE : View.GONE);
        row.valueLayout.setHelperText(ExtraTypes.hintFor(type));
    }

    // ─── JSON import ─────────────────────────────────────────────────────

    /**
     * Converts a JSON object into typed entries: integers → Integer/Long,
     * decimals → Double, booleans, strings, null → Null, objects → Bundle,
     * arrays → int[] / double[] / boolean[] / String[] by element type.
     */
    public static List<IntentModel.ExtraEntry> entriesFromJson(String json) throws JSONException {
        JSONObject o = new JSONObject(json);
        return entriesFromJson(o);
    }

    public static List<IntentModel.ExtraEntry> entriesFromJson(JSONObject o) throws JSONException {
        List<IntentModel.ExtraEntry> out = new ArrayList<>();
        Iterator<String> it = o.keys();
        while (it.hasNext()) {
            String key = it.next();
            Object v = o.get(key);
            out.add(entryFromJsonValue(key, v));
        }
        return out;
    }

    private static IntentModel.ExtraEntry entryFromJsonValue(String key, Object v) throws JSONException {
        if (v == null || v == JSONObject.NULL) return new IntentModel.ExtraEntry(key, "", ExtraTypes.NULL);
        if (v instanceof JSONObject) return IntentModel.ExtraEntry.bundle(key, entriesFromJson((JSONObject) v));
        if (v instanceof JSONArray) {
            JSONArray arr = (JSONArray) v;
            boolean allInt = arr.length() > 0, allNum = arr.length() > 0, allBool = arr.length() > 0;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arr.length(); i++) {
                Object e = arr.get(i);
                if (!(e instanceof Integer || e instanceof Long)) allInt = false;
                if (!(e instanceof Number)) allNum = false;
                if (!(e instanceof Boolean)) allBool = false;
                if (i > 0) sb.append(',');
                sb.append(e == JSONObject.NULL ? "" : String.valueOf(e));
            }
            String type = allInt ? ExtraTypes.INT_ARRAY : allNum ? ExtraTypes.DOUBLE_ARRAY
                    : allBool ? ExtraTypes.BOOLEAN_ARRAY : ExtraTypes.STRING_ARRAY;
            return new IntentModel.ExtraEntry(key, sb.toString(), type);
        }
        if (v instanceof Boolean) return new IntentModel.ExtraEntry(key, v.toString(), ExtraTypes.BOOLEAN);
        if (v instanceof Integer) return new IntentModel.ExtraEntry(key, v.toString(), ExtraTypes.INT);
        if (v instanceof Long) return new IntentModel.ExtraEntry(key, v.toString(), ExtraTypes.LONG);
        if (v instanceof Number) return new IntentModel.ExtraEntry(key, v.toString(), ExtraTypes.DOUBLE);
        return new IntentModel.ExtraEntry(key, v.toString(), ExtraTypes.STRING);
    }
}
