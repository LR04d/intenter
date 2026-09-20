package com.open.intenter;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Favorites (named intents), the builder draft, pending "load this" requests
 * and the built-in presets. History stays in {@link HistoryManager}.
 */
public final class SavedStore {

    private static final String TAG = "SavedStore";
    private static final String PREFS = "intenter_saved";
    private static final String KEY_FAVORITES = "favorites_json";
    private static final String KEY_DRAFT = "draft_json";
    private static final String KEY_PENDING = "pending_load_json";
    private static final String KEY_PROVIDER_DRAFT = "provider_draft_json";
    private static final String KEY_LISTENER = "listener_json";

    private static SavedStore instance;
    private final SharedPreferences prefs;

    private SavedStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized SavedStore get(Context context) {
        if (instance == null) instance = new SavedStore(context);
        return instance;
    }

    // ─── Favorites ───────────────────────────────────────────────────────

    public List<IntentModel> favorites() {
        return readList(KEY_FAVORITES);
    }

    public void saveFavorite(IntentModel model) {
        List<IntentModel> list = favorites();
        if (model.timestamp == 0) model.timestamp = System.currentTimeMillis();
        if (model.label.isEmpty()) model.label = model.generateLabel();
        // Replace an existing favorite with the same name.
        for (int i = 0; i < list.size(); i++) {
            if (!model.name.isEmpty() && model.name.equals(list.get(i).name)) {
                list.set(i, model);
                writeList(KEY_FAVORITES, list);
                return;
            }
        }
        list.add(0, model);
        writeList(KEY_FAVORITES, list);
    }

    public void deleteFavorite(int index) {
        List<IntentModel> list = favorites();
        if (index >= 0 && index < list.size()) {
            list.remove(index);
            writeList(KEY_FAVORITES, list);
        }
    }

    public void replaceFavorites(List<IntentModel> list) {
        writeList(KEY_FAVORITES, list);
    }

    // ─── Draft / pending load ────────────────────────────────────────────

    public void saveDraft(IntentModel model) {
        prefs.edit().putString(KEY_DRAFT, model.toJsonString()).apply();
    }

    public IntentModel draft() {
        return readOne(KEY_DRAFT);
    }

    public void clearDraft() {
        prefs.edit().remove(KEY_DRAFT).apply();
    }

    public void setPendingLoad(IntentModel model) {
        prefs.edit().putString(KEY_PENDING, model.toJsonString()).apply();
    }

    /** Returns and clears the pending model, if any. */
    public IntentModel takePendingLoad() {
        IntentModel m = readOne(KEY_PENDING);
        if (m != null) prefs.edit().remove(KEY_PENDING).apply();
        return m;
    }

    public void saveProviderDraft(JSONObject json) {
        prefs.edit().putString(KEY_PROVIDER_DRAFT, json.toString()).apply();
    }

    public JSONObject providerDraft() {
        String s = prefs.getString(KEY_PROVIDER_DRAFT, null);
        if (s == null) return null;
        try { return new JSONObject(s); } catch (JSONException e) { return null; }
    }

    public void saveListenerConfig(JSONObject json) {
        prefs.edit().putString(KEY_LISTENER, json.toString()).apply();
    }

    public JSONObject listenerConfig() {
        String s = prefs.getString(KEY_LISTENER, null);
        if (s == null) return null;
        try { return new JSONObject(s); } catch (JSONException e) { return null; }
    }

    // ─── Export / import ─────────────────────────────────────────────────

    public String exportAll(List<IntentModel> history) {
        JSONObject root = new JSONObject();
        try {
            root.put("app", "intenter");
            root.put("version", 2);
            root.put("exportedAt", System.currentTimeMillis());
            JSONArray fav = new JSONArray();
            for (IntentModel m : favorites()) fav.put(m.toJson());
            root.put("favorites", fav);
            JSONArray hist = new JSONArray();
            for (IntentModel m : history) hist.put(m.toJson());
            root.put("history", hist);
            return root.toString(2);
        } catch (JSONException e) {
            return "{}";
        }
    }

    /** Imports favorites (and optionally history) from an export file. Returns count imported. */
    public int importAll(String json, HistoryManager history) throws JSONException {
        JSONObject root = new JSONObject(json);
        int count = 0;
        JSONArray fav = root.optJSONArray("favorites");
        if (fav != null) {
            List<IntentModel> list = favorites();
            for (int i = 0; i < fav.length(); i++) {
                IntentModel m = IntentModel.fromJson(fav.getJSONObject(i));
                boolean replaced = false;
                for (int j = 0; j < list.size(); j++) {
                    if (!m.name.isEmpty() && m.name.equals(list.get(j).name)) { list.set(j, m); replaced = true; break; }
                }
                if (!replaced) list.add(m);
                count++;
            }
            writeList(KEY_FAVORITES, list);
        }
        JSONArray hist = root.optJSONArray("history");
        if (hist != null && history != null) {
            for (int i = hist.length() - 1; i >= 0; i--) {
                history.save(IntentModel.fromJson(hist.getJSONObject(i)));
                count++;
            }
        }
        if (fav == null && hist == null) {
            // A single model file
            IntentModel m = IntentModel.fromJson(root);
            if (m.name.isEmpty()) m.name = m.generateLabel();
            saveFavorite(m);
            count = 1;
        }
        return count;
    }

    // ─── Presets ─────────────────────────────────────────────────────────

    public static List<IntentModel> presets() {
        List<IntentModel> list = new ArrayList<>();
        list.add(preset("Open URL in browser", "Implicit VIEW with an https URI",
                IntentModel.MODE_ACTIVITY, "android.intent.action.VIEW", "https://example.com", null, null, null));
        list.add(preset("Share text", "SEND text/plain wrapped in a chooser",
                IntentModel.MODE_ACTIVITY, "android.intent.action.SEND", null, "text/plain",
                new String[][]{{"android.intent.extra.TEXT", "Hello from Intenter", ExtraTypes.STRING},
                        {"android.intent.extra.SUBJECT", "Subject", ExtraTypes.STRING}}, null));
        list.get(list.size() - 1).useChooser = true;
        list.get(list.size() - 1).chooserTitle = "Share with";
        list.add(preset("Dial a number", "DIAL with tel: URI (no CALL_PHONE permission needed)",
                IntentModel.MODE_ACTIVITY, "android.intent.action.DIAL", "tel:+15551234567", null, null, null));
        list.add(preset("Compose SMS", "SENDTO with sms: URI and body extra",
                IntentModel.MODE_ACTIVITY, "android.intent.action.SENDTO", "sms:+15551234567", null,
                new String[][]{{"sms_body", "Test message", ExtraTypes.STRING}}, null));
        list.add(preset("Compose email", "SENDTO mailto: with subject and body",
                IntentModel.MODE_ACTIVITY, "android.intent.action.SENDTO", "mailto:test@example.com", null,
                new String[][]{{"android.intent.extra.SUBJECT", "Subject", ExtraTypes.STRING},
                        {"android.intent.extra.TEXT", "Body", ExtraTypes.STRING}}, null));
        list.add(preset("Show map location", "VIEW geo: URI",
                IntentModel.MODE_ACTIVITY, "android.intent.action.VIEW", "geo:37.7749,-122.4194?q=San+Francisco", null, null, null));
        list.add(preset("Pick an image (for result)", "GET_CONTENT image/* returns a content URI",
                IntentModel.MODE_ACTIVITY_RESULT, "android.intent.action.GET_CONTENT", null, "image/*", null,
                new String[]{"android.intent.category.OPENABLE"}));
        list.add(preset("Open document (SAF)", "OPEN_DOCUMENT */* with OPENABLE category",
                IntentModel.MODE_ACTIVITY_RESULT, "android.intent.action.OPEN_DOCUMENT", null, "*/*", null,
                new String[]{"android.intent.category.OPENABLE"}));
        list.add(preset("App details in Settings", "Opens the system settings page of a package",
                IntentModel.MODE_ACTIVITY, "android.settings.APPLICATION_DETAILS_SETTINGS", "package:com.open.intenter", null, null, null));
        list.add(preset("Deep link (custom scheme)", "VIEW + BROWSABLE, what a browser would send",
                IntentModel.MODE_ACTIVITY, "android.intent.action.VIEW", "myapp://open/screen?id=42", null, null,
                new String[]{"android.intent.category.BROWSABLE", "android.intent.category.DEFAULT"}));
        list.add(preset("Custom broadcast", "Implicit broadcast with typed extras",
                IntentModel.MODE_BROADCAST, "com.example.ACTION_TEST", null, null,
                new String[][]{{"count", "3", ExtraTypes.INT}, {"enabled", "true", ExtraTypes.BOOLEAN},
                        {"ids", "1,2,3", ExtraTypes.INT_ARRAY}}, null));
        list.add(preset("Ordered broadcast with result", "Sends ordered and shows the collected result",
                IntentModel.MODE_ORDERED_BROADCAST, "com.example.ACTION_ORDERED", null, null,
                new String[][]{{"payload", "ping", ExtraTypes.STRING}}, null));
        IntentModel nested = preset("Nested bundle + intent extra", "Bundle-in-bundle and an Intent as extra value",
                IntentModel.MODE_ACTIVITY, null, null, null, null, null);
        nested.useComponent = true;
        nested.packageName = "com.open.intenter";
        nested.componentName = "com.open.intenter.ReceiverActivity";
        nested.useExtras = true;
        List<IntentModel.ExtraEntry> inner = new ArrayList<>();
        inner.add(new IntentModel.ExtraEntry("user_id", "1001", ExtraTypes.LONG));
        inner.add(new IntentModel.ExtraEntry("flags", "true,false", ExtraTypes.BOOLEAN_ARRAY));
        nested.extras.add(IntentModel.ExtraEntry.bundle("payload", inner));
        nested.extras.add(new IntentModel.ExtraEntry("next", "intent:#Intent;action=android.intent.action.VIEW;S.note=inner;end", ExtraTypes.INTENT));
        nested.extras.add(new IntentModel.ExtraEntry("nothing", "", ExtraTypes.NULL));
        list.add(nested);
        IntentModel self = preset("Send to Intenter receiver", "Explicit intent to this app's exported receiver activity",
                IntentModel.MODE_ACTIVITY_RESULT, "android.intent.action.VIEW", "intenter://echo?x=1", null,
                new String[][]{{"hello", "world", ExtraTypes.STRING}, {"n", "7", ExtraTypes.INT}}, null);
        self.useComponent = true;
        self.packageName = "com.open.intenter";
        self.componentName = "com.open.intenter.ReceiverActivity";
        list.add(self);
        IntentModel grant = preset("Share a content URI with grant", "SEND with FLAG_GRANT_READ_URI_PERMISSION and ClipData",
                IntentModel.MODE_ACTIVITY, "android.intent.action.SEND", null, "image/png",
                new String[][]{{"android.intent.extra.STREAM", "content://media/external/images/media/1", ExtraTypes.URI}}, null);
        grant.useFlags = true;
        grant.flagNames.add("FLAG_GRANT_READ_URI_PERMISSION");
        grant.useClipData = true;
        grant.clipDataMimeTypes.add("image/png");
        grant.clipDataItems.add(new IntentModel.ClipDataItem("Uri", "content://media/external/images/media/1"));
        list.add(grant);
        return list;
    }

    private static IntentModel preset(String name, String description, String mode, String action,
                                      String data, String mime, String[][] extras, String[] categories) {
        IntentModel m = new IntentModel();
        m.name = name;
        m.description = description;
        m.launchType = mode;
        if (action != null) { m.useAction = true; m.actions.add(action); m.action = action; }
        if (data != null || mime != null) {
            m.useData = true;
            m.dataUri = data == null ? "" : data;
            m.mimeType = mime == null ? "" : mime;
        }
        if (extras != null) {
            m.useExtras = true;
            for (String[] e : extras) m.extras.add(new IntentModel.ExtraEntry(e[0], e[1], e[2]));
        }
        if (categories != null) {
            m.useCategory = true;
            for (String c : categories) m.categories.add(c);
        }
        m.label = m.generateLabel();
        return m;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private List<IntentModel> readList(String key) {
        List<IntentModel> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(key, "[]"));
            for (int i = 0; i < arr.length(); i++) list.add(IntentModel.fromJson(arr.getJSONObject(i)));
        } catch (JSONException e) {
            Log.e(TAG, "read " + key, e);
        }
        return list;
    }

    private void writeList(String key, List<IntentModel> list) {
        JSONArray arr = new JSONArray();
        for (IntentModel m : list) {
            try { arr.put(m.toJson()); } catch (JSONException e) { Log.e(TAG, "write", e); }
        }
        prefs.edit().putString(key, arr.toString()).apply();
    }

    private IntentModel readOne(String key) {
        String s = prefs.getString(key, null);
        if (s == null) return null;
        try {
            return IntentModel.fromJsonString(s);
        } catch (JSONException e) {
            return null;
        }
    }
}
