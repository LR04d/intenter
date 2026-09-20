package com.open.intenter;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Content provider client: query / insert / update / delete / call / getType /
 * openInputStream against any content URI, with the result rendered as text.
 */
public class ProviderFragment extends Fragment {

    private static final int MAX_ROWS = 200;
    private static final int MAX_READ = 8192;

    private TextInputEditText uriInput, projectionInput, selectionInput, selectionArgsInput, sortOrderInput,
            whereInput, whereArgsInput, callMethodInput, callArgInput;
    private TextView providerInfo, resultText;
    private MaterialButtonToggleGroup opGroup;
    private View queryLayout, valuesLayout, whereLayout, callLayout;
    private MaterialButton runButton;
    private ExtrasEditor valuesEditor, callExtrasEditor;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Runnable infoRunnable = this::updateProviderInfo;
    private int infoGeneration = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_provider, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        uriInput = v.findViewById(R.id.providerUriInput);
        projectionInput = v.findViewById(R.id.projectionInput);
        selectionInput = v.findViewById(R.id.selectionInput);
        selectionArgsInput = v.findViewById(R.id.selectionArgsInput);
        sortOrderInput = v.findViewById(R.id.sortOrderInput);
        whereInput = v.findViewById(R.id.whereInput);
        whereArgsInput = v.findViewById(R.id.whereArgsInput);
        callMethodInput = v.findViewById(R.id.callMethodInput);
        callArgInput = v.findViewById(R.id.callArgInput);
        providerInfo = v.findViewById(R.id.providerInfo);
        resultText = v.findViewById(R.id.providerResultText);
        opGroup = v.findViewById(R.id.opGroup);
        queryLayout = v.findViewById(R.id.queryLayout);
        valuesLayout = v.findViewById(R.id.valuesLayout);
        whereLayout = v.findViewById(R.id.whereLayout);
        callLayout = v.findViewById(R.id.callLayout);
        runButton = v.findViewById(R.id.runProviderButton);

        valuesEditor = new ExtrasEditor(requireContext(), v.findViewById(R.id.valuesContainer), ExtraTypes.CONTENT_VALUE_TYPES, null);
        callExtrasEditor = new ExtrasEditor(requireContext(), v.findViewById(R.id.callExtrasContainer), ExtraTypes.ALL, null);
        v.findViewById(R.id.addValueButton).setOnClickListener(x -> valuesEditor.add());
        v.findViewById(R.id.addCallExtraButton).setOnClickListener(x -> callExtrasEditor.add());
        v.findViewById(R.id.browseProviderButton).setOnClickListener(x -> showPicker());
        v.findViewById(R.id.copyProviderResultButton).setOnClickListener(x ->
                UiUtil.copy(requireContext(), "Provider result", resultText.getText().toString()));
        runButton.setOnClickListener(x -> run());

        opGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> { if (isChecked) applyOp(checkedId); });
        uriInput.addTextChangedListener(UiUtil.watcher(() -> {
            handler.removeCallbacks(infoRunnable);
            handler.postDelayed(infoRunnable, 200);
        }));

        restoreDraft();
        applyOp(opGroup.getCheckedButtonId());
        updateProviderInfo();
    }

    @Override
    public void onPause() {
        super.onPause();
        saveDraft();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    public void setUri(String uri) {
        if (uriInput != null) uriInput.setText(uri);
    }

    // ─── Ops ─────────────────────────────────────────────────────────────

    private void applyOp(int id) {
        boolean query = id == R.id.opQuery, insert = id == R.id.opInsert, update = id == R.id.opUpdate,
                delete = id == R.id.opDelete, call = id == R.id.opCall;
        queryLayout.setVisibility(query ? View.VISIBLE : View.GONE);
        valuesLayout.setVisibility(insert || update ? View.VISIBLE : View.GONE);
        whereLayout.setVisibility(update || delete ? View.VISIBLE : View.GONE);
        callLayout.setVisibility(call ? View.VISIBLE : View.GONE);
        if (query) runButton.setText("Run query");
        else if (insert) runButton.setText("Insert row");
        else if (update) runButton.setText("Update rows");
        else if (delete) runButton.setText("Delete rows");
        else if (call) runButton.setText("Call method");
        else if (id == R.id.opType) runButton.setText("Get type");
        else runButton.setText("Read stream");
    }

    private void showPicker() {
        ComponentPickerDialog dialog = ComponentPickerDialog.newInstance(true);
        dialog.setListener(new ComponentPickerDialog.Listener() {
            @Override public void onPackageOnly(String packageName) {}
            @Override
            public void onComponentSelected(ComponentPickerDialog.ComponentEntry e) {
                if (e.authority != null) {
                    String first = e.authority.split(";")[0];
                    uriInput.setText("content://" + first + "/");
                }
            }
        });
        dialog.show(getParentFragmentManager(), "provider_picker");
    }

    private void updateProviderInfo() {
        if (!isAdded()) return;
        String uriStr = UiUtil.text(uriInput);
        if (uriStr.isEmpty()) {
            providerInfo.setText("Enter an authority to see who owns it");
            return;
        }
        Uri uri = Uri.parse(uriStr);
        String authority = uri.getAuthority();
        if (authority == null || authority.isEmpty()) {
            providerInfo.setText("No authority in URI");
            return;
        }
        final int gen = ++infoGeneration;
        final PackageManager pm = requireContext().getPackageManager();
        executor.execute(() -> {
            String text;
            try {
                ProviderInfo pi = pm.resolveContentProvider(authority, PackageManager.MATCH_DISABLED_COMPONENTS);
                if (pi == null) {
                    text = "No provider for authority \"" + authority + "\"";
                } else {
                    StringBuilder sb = new StringBuilder();
                    sb.append(pi.packageName).append('/').append(pi.name).append('\n');
                    sb.append(pi.exported ? "exported" : "NOT exported");
                    if (!pi.enabled) sb.append(" · disabled");
                    sb.append(" · read: ").append(pi.readPermission == null ? "none" : pi.readPermission);
                    sb.append(" · write: ").append(pi.writePermission == null ? "none" : pi.writePermission);
                    if (pi.grantUriPermissions) sb.append(" · grantUriPermissions");
                    if (pi.pathPermissions != null && pi.pathPermissions.length > 0) sb.append(" · ").append(pi.pathPermissions.length).append(" path permission(s)");
                    text = sb.toString();
                }
            } catch (RuntimeException e) {
                text = "Lookup failed: " + UiUtil.describeThrowable(e);
            }
            final String t = text;
            handler.post(() -> { if (gen == infoGeneration && isAdded()) providerInfo.setText(t); });
        });
    }

    private void run() {
        final String uriStr = UiUtil.text(uriInput);
        if (uriStr.isEmpty()) {
            UiUtil.showError(requireContext(), "Enter a content:// URI first");
            return;
        }
        final Uri uri = Uri.parse(uriStr);
        final int op = opGroup.getCheckedButtonId();
        final String[] projection = IntentBuilder.splitTrimmed(UiUtil.text(projectionInput));
        final String selection = emptyToNull(UiUtil.text(selectionInput));
        final String[] selectionArgs = argsOrNull(UiUtil.raw(selectionArgsInput));
        final String sort = emptyToNull(UiUtil.text(sortOrderInput));
        final String where = emptyToNull(UiUtil.text(whereInput));
        final String[] whereArgs = argsOrNull(UiUtil.raw(whereArgsInput));
        final String method = UiUtil.text(callMethodInput);
        final String arg = emptyToNull(UiUtil.text(callArgInput));
        final List<IntentModel.ExtraEntry> valueEntries = valuesEditor.entries();
        final List<IntentModel.ExtraEntry> callEntries = callExtrasEditor.entries();
        final ContentResolver cr = requireContext().getContentResolver();
        final EventStore events = EventStore.get(requireContext());

        resultText.setText("Running…");
        runButton.setEnabled(false);
        executor.execute(() -> {
            String title, result;
            boolean error = false;
            long start = System.currentTimeMillis();
            try {
                if (op == R.id.opQuery) {
                    title = "query " + uri;
                    result = query(cr, uri, projection.length == 0 ? null : projection, selection, selectionArgs, sort);
                } else if (op == R.id.opInsert) {
                    title = "insert " + uri;
                    Uri out = cr.insert(uri, contentValues(valueEntries));
                    result = "insert returned: " + out;
                } else if (op == R.id.opUpdate) {
                    title = "update " + uri;
                    int n = cr.update(uri, contentValues(valueEntries), where, whereArgs);
                    result = "update affected " + n + " row(s)";
                } else if (op == R.id.opDelete) {
                    title = "delete " + uri;
                    int n = cr.delete(uri, where, whereArgs);
                    result = "delete removed " + n + " row(s)";
                } else if (op == R.id.opCall) {
                    title = "call " + method + " on " + uri;
                    if (method.isEmpty()) throw new IllegalArgumentException("Method name is required");
                    Bundle extras = callEntries.isEmpty() ? null : IntentBuilder.buildBundle(callEntries);
                    Bundle out = cr.call(uri, method, arg, extras);
                    result = out == null ? "call returned null" : "call returned Bundle:\n" + IntentDumper.dumpBundle(out);
                } else if (op == R.id.opType) {
                    title = "getType " + uri;
                    String type = cr.getType(uri);
                    String[] streams = null;
                    try { streams = cr.getStreamTypes(uri, "*/*"); } catch (RuntimeException ignored) {}
                    result = "getType: " + type + "\ngetStreamTypes(*/*): " + (streams == null ? "null" : java.util.Arrays.toString(streams));
                } else {
                    title = "read " + uri;
                    result = read(cr, uri);
                }
            } catch (Throwable t) {
                title = "provider error";
                result = "ERROR " + UiUtil.describeThrowable(t);
                error = true;
            }
            long ms = System.currentTimeMillis() - start;
            final String fTitle = title, fResult = result + "\n\n(" + ms + " ms)";
            final boolean fError = error;
            events.add(fError ? EventStore.Kind.ERROR : EventStore.Kind.PROVIDER, fTitle,
                    fResult.split("\n")[0], fResult);
            handler.post(() -> {
                if (!isAdded()) return;
                resultText.setText(fResult);
                runButton.setEnabled(true);
            });
        });
    }

    private static String query(ContentResolver cr, Uri uri, String[] projection, String selection,
                                String[] args, String sort) {
        try (Cursor c = cr.query(uri, projection, selection, args, sort)) {
            if (c == null) return "query returned null cursor";
            int cols = c.getColumnCount();
            StringBuilder sb = new StringBuilder();
            sb.append(c.getCount()).append(" row(s), ").append(cols).append(" column(s)\n\n");
            String[] names = c.getColumnNames();
            for (int i = 0; i < cols; i++) {
                if (i > 0) sb.append(" | ");
                sb.append(names[i]);
            }
            sb.append('\n');
            for (int i = 0; i < cols; i++) {
                if (i > 0) sb.append("-+-");
                for (int k = 0; k < Math.max(3, names[i].length()); k++) sb.append('-');
            }
            sb.append('\n');
            int rows = 0;
            while (c.moveToNext() && rows < MAX_ROWS) {
                for (int i = 0; i < cols; i++) {
                    if (i > 0) sb.append(" | ");
                    sb.append(cellToString(c, i));
                }
                sb.append('\n');
                rows++;
            }
            if (c.getCount() > MAX_ROWS) sb.append("… ").append(c.getCount() - MAX_ROWS).append(" more row(s) not shown\n");
            Bundle extras = c.getExtras();
            if (extras != null && !extras.isEmpty()) sb.append("\nCursor extras:\n").append(IntentDumper.dumpBundle(extras));
            return sb.toString();
        }
    }

    private static String cellToString(Cursor c, int i) {
        try {
            switch (c.getType(i)) {
                case Cursor.FIELD_TYPE_NULL: return "NULL";
                case Cursor.FIELD_TYPE_INTEGER: return String.valueOf(c.getLong(i));
                case Cursor.FIELD_TYPE_FLOAT: return String.valueOf(c.getDouble(i));
                case Cursor.FIELD_TYPE_BLOB: {
                    byte[] b = c.getBlob(i);
                    return "blob[" + (b == null ? 0 : b.length) + "]";
                }
                default: {
                    String s = c.getString(i);
                    if (s == null) return "NULL";
                    s = s.replace("\n", "\\n");
                    return s.length() > 120 ? s.substring(0, 120) + "…" : s;
                }
            }
        } catch (RuntimeException e) {
            return "<" + e.getClass().getSimpleName() + ">";
        }
    }

    private static String read(ContentResolver cr, Uri uri) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (AssetFileDescriptor afd = cr.openAssetFileDescriptor(uri, "r")) {
            if (afd != null) sb.append("Declared length: ").append(afd.getDeclaredLength()).append(" bytes\n");
        } catch (Exception e) {
            sb.append("openAssetFileDescriptor: ").append(UiUtil.describeThrowable(e)).append('\n');
        }
        try (InputStream in = cr.openInputStream(uri)) {
            if (in == null) return sb.append("openInputStream returned null").toString();
            byte[] buf = new byte[MAX_READ];
            int total = 0, n;
            while (total < MAX_READ && (n = in.read(buf, total, MAX_READ - total)) > 0) total += n;
            sb.append("Read ").append(total).append(" byte(s)").append(total == MAX_READ ? " (truncated)" : "").append("\n\n");
            boolean text = true;
            for (int i = 0; i < total; i++) {
                byte b = buf[i];
                if (b >= 0 && b < 0x20 && b != '\n' && b != '\r' && b != '\t') { text = false; break; }
            }
            if (text) {
                sb.append(new String(buf, 0, total, StandardCharsets.UTF_8));
            } else {
                for (int i = 0; i < total; i++) {
                    if (i > 0 && i % 16 == 0) sb.append('\n');
                    sb.append(String.format("%02X ", buf[i]));
                }
            }
        }
        return sb.toString();
    }

    private static ContentValues contentValues(List<IntentModel.ExtraEntry> entries) {
        ContentValues cv = new ContentValues();
        for (IntentModel.ExtraEntry e : entries) {
            if (e.key.isEmpty()) continue;
            String v = e.value == null ? "" : e.value;
            switch (e.type) {
                case ExtraTypes.INT: cv.put(e.key, IntentBuilder.parseInt(v)); break;
                case ExtraTypes.LONG: cv.put(e.key, IntentBuilder.parseLong(v)); break;
                case ExtraTypes.SHORT: cv.put(e.key, (short) IntentBuilder.parseInt(v)); break;
                case ExtraTypes.BYTE: cv.put(e.key, (byte) IntentBuilder.parseInt(v)); break;
                case ExtraTypes.BOOLEAN: cv.put(e.key, IntentBuilder.parseBoolean(v)); break;
                case ExtraTypes.FLOAT: cv.put(e.key, Float.parseFloat(v.trim())); break;
                case ExtraTypes.DOUBLE: cv.put(e.key, Double.parseDouble(v.trim())); break;
                case ExtraTypes.BYTE_ARRAY: cv.put(e.key, IntentBuilder.parseBytes(v)); break;
                case ExtraTypes.NULL: cv.putNull(e.key); break;
                default: cv.put(e.key, v); break;
            }
        }
        return cv;
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    private static String[] argsOrNull(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        return IntentBuilder.splitTrimmed(s);
    }

    // ─── Draft ───────────────────────────────────────────────────────────

    private void saveDraft() {
        try {
            JSONObject o = new JSONObject();
            o.put("uri", UiUtil.raw(uriInput));
            o.put("projection", UiUtil.raw(projectionInput));
            o.put("selection", UiUtil.raw(selectionInput));
            o.put("selectionArgs", UiUtil.raw(selectionArgsInput));
            o.put("sort", UiUtil.raw(sortOrderInput));
            o.put("where", UiUtil.raw(whereInput));
            o.put("whereArgs", UiUtil.raw(whereArgsInput));
            o.put("method", UiUtil.raw(callMethodInput));
            o.put("arg", UiUtil.raw(callArgInput));
            o.put("op", opGroup.getCheckedButtonId());
            JSONArray values = new JSONArray();
            for (IntentModel.ExtraEntry e : valuesEditor.entries()) values.put(e.toJson());
            o.put("values", values);
            JSONArray call = new JSONArray();
            for (IntentModel.ExtraEntry e : callExtrasEditor.entries()) call.put(e.toJson());
            o.put("callExtras", call);
            SavedStore.get(requireContext()).saveProviderDraft(o);
        } catch (JSONException ignored) {}
    }

    private void restoreDraft() {
        JSONObject o = SavedStore.get(requireContext()).providerDraft();
        if (o == null) return;
        uriInput.setText(o.optString("uri", ""));
        projectionInput.setText(o.optString("projection", ""));
        selectionInput.setText(o.optString("selection", ""));
        selectionArgsInput.setText(o.optString("selectionArgs", ""));
        sortOrderInput.setText(o.optString("sort", ""));
        whereInput.setText(o.optString("where", ""));
        whereArgsInput.setText(o.optString("whereArgs", ""));
        callMethodInput.setText(o.optString("method", ""));
        callArgInput.setText(o.optString("arg", ""));
        int op = o.optInt("op", R.id.opQuery);
        if (op != View.NO_ID && opGroup.findViewById(op) != null) opGroup.check(op);
        try {
            JSONArray values = o.optJSONArray("values");
            if (values != null) {
                java.util.List<IntentModel.ExtraEntry> list = new java.util.ArrayList<>();
                for (int i = 0; i < values.length(); i++) list.add(IntentModel.ExtraEntry.fromJson(values.getJSONObject(i)));
                valuesEditor.set(list);
            }
            JSONArray call = o.optJSONArray("callExtras");
            if (call != null) {
                java.util.List<IntentModel.ExtraEntry> list = new java.util.ArrayList<>();
                for (int i = 0; i < call.length(); i++) list.add(IntentModel.ExtraEntry.fromJson(call.getJSONObject(i)));
                callExtrasEditor.set(list);
            }
        } catch (JSONException ignored) {}
    }
}
