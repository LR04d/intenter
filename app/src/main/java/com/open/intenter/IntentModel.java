package com.open.intenter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Data model representing a fully configured Intent.
 * Serializable to/from JSON for history persistence.
 */
public class IntentModel {

    public String label = "";          // auto-generated summary for history display
    public long timestamp;             // millis

    // Component
    public boolean useComponent;
    public String packageName = "";
    public String componentName = "";

    // Action
    public boolean useAction;
    public String action = "";

    // Data
    public boolean useData;
    public String dataUri = "";
    public String mimeType = "";

    // Categories
    public boolean useCategory;
    public List<String> categories = new ArrayList<>();

    // Extras
    public boolean useExtras;
    public List<ExtraEntry> extras = new ArrayList<>();

    // Bundles
    public boolean useBundle;
    public List<BundleEntry> bundles = new ArrayList<>();

    // Flags
    public boolean useFlags;
    public List<String> flagNames = new ArrayList<>();   // chip tag names
    public String customFlags = "";

    // Launch type chip id name (chipActivity, chipService, etc.)
    public String launchType = "chipActivity";

    // Chooser
    public boolean useChooser;
    public String chooserTitle = "";

    // Permission (for broadcasts)
    public String permission = "";

    // ─── Nested types ────────────────────────────────────────────────────

    public static class ExtraEntry {
        public String key = "";
        public String value = "";
        public String type = "String";

        public ExtraEntry() {}
        public ExtraEntry(String key, String value, String type) {
            this.key = key;
            this.value = value;
            this.type = type;
        }

        public JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("key", key);
            o.put("value", value);
            o.put("type", type);
            return o;
        }

        public static ExtraEntry fromJson(JSONObject o) throws JSONException {
            ExtraEntry e = new ExtraEntry();
            e.key = o.optString("key", "");
            e.value = o.optString("value", "");
            e.type = o.optString("type", "String");
            return e;
        }
    }

    public static class BundleEntry {
        public String key = "";
        public List<ExtraEntry> extras = new ArrayList<>();

        public BundleEntry() {}

        public JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("key", key);
            JSONArray arr = new JSONArray();
            for (ExtraEntry e : extras) arr.put(e.toJson());
            o.put("extras", arr);
            return o;
        }

        public static BundleEntry fromJson(JSONObject o) throws JSONException {
            BundleEntry b = new BundleEntry();
            b.key = o.optString("key", "");
            JSONArray arr = o.optJSONArray("extras");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    b.extras.add(ExtraEntry.fromJson(arr.getJSONObject(i)));
                }
            }
            return b;
        }
    }

    // ─── Serialization ───────────────────────────────────────────────────

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("label", label);
        o.put("timestamp", timestamp);

        o.put("useComponent", useComponent);
        o.put("packageName", packageName);
        o.put("componentName", componentName);

        o.put("useAction", useAction);
        o.put("action", action);

        o.put("useData", useData);
        o.put("dataUri", dataUri);
        o.put("mimeType", mimeType);

        o.put("useCategory", useCategory);
        JSONArray catArr = new JSONArray();
        for (String c : categories) catArr.put(c);
        o.put("categories", catArr);

        o.put("useExtras", useExtras);
        JSONArray extArr = new JSONArray();
        for (ExtraEntry e : extras) extArr.put(e.toJson());
        o.put("extras", extArr);

        o.put("useBundle", useBundle);
        JSONArray bunArr = new JSONArray();
        for (BundleEntry b : bundles) bunArr.put(b.toJson());
        o.put("bundles", bunArr);

        o.put("useFlags", useFlags);
        JSONArray flagArr = new JSONArray();
        for (String f : flagNames) flagArr.put(f);
        o.put("flagNames", flagArr);
        o.put("customFlags", customFlags);

        o.put("launchType", launchType);

        o.put("useChooser", useChooser);
        o.put("chooserTitle", chooserTitle);
        o.put("permission", permission);

        return o;
    }

    public static IntentModel fromJson(JSONObject o) throws JSONException {
        IntentModel m = new IntentModel();
        m.label = o.optString("label", "");
        m.timestamp = o.optLong("timestamp", 0);

        m.useComponent = o.optBoolean("useComponent", false);
        m.packageName = o.optString("packageName", "");
        m.componentName = o.optString("componentName", "");

        m.useAction = o.optBoolean("useAction", false);
        m.action = o.optString("action", "");

        m.useData = o.optBoolean("useData", false);
        m.dataUri = o.optString("dataUri", "");
        m.mimeType = o.optString("mimeType", "");

        m.useCategory = o.optBoolean("useCategory", false);
        JSONArray catArr = o.optJSONArray("categories");
        if (catArr != null) {
            for (int i = 0; i < catArr.length(); i++) m.categories.add(catArr.getString(i));
        }

        m.useExtras = o.optBoolean("useExtras", false);
        JSONArray extArr = o.optJSONArray("extras");
        if (extArr != null) {
            for (int i = 0; i < extArr.length(); i++) {
                m.extras.add(ExtraEntry.fromJson(extArr.getJSONObject(i)));
            }
        }

        m.useBundle = o.optBoolean("useBundle", false);
        JSONArray bunArr = o.optJSONArray("bundles");
        if (bunArr != null) {
            for (int i = 0; i < bunArr.length(); i++) {
                m.bundles.add(BundleEntry.fromJson(bunArr.getJSONObject(i)));
            }
        }

        m.useFlags = o.optBoolean("useFlags", false);
        JSONArray flagArr = o.optJSONArray("flagNames");
        if (flagArr != null) {
            for (int i = 0; i < flagArr.length(); i++) m.flagNames.add(flagArr.getString(i));
        }
        m.customFlags = o.optString("customFlags", "");

        m.launchType = o.optString("launchType", "chipActivity");

        m.useChooser = o.optBoolean("useChooser", false);
        m.chooserTitle = o.optString("chooserTitle", "");
        m.permission = o.optString("permission", "");

        return m;
    }

    /** Generate a human-readable label for the history list */
    public String generateLabel() {
        StringBuilder sb = new StringBuilder();
        if (!action.isEmpty()) {
            String shortAction = action;
            if (shortAction.startsWith("android.intent.action.")) {
                shortAction = shortAction.substring("android.intent.action.".length());
            }
            sb.append(shortAction);
        }
        if (!packageName.isEmpty()) {
            if (sb.length() > 0) sb.append(" → ");
            sb.append(packageName);
            if (!componentName.isEmpty()) {
                String shortComp = componentName;
                if (shortComp.contains(".")) {
                    shortComp = shortComp.substring(shortComp.lastIndexOf('.') + 1);
                }
                sb.append("/").append(shortComp);
            }
        }
        if (!dataUri.isEmpty()) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append(dataUri.length() > 40 ? dataUri.substring(0, 40) + "…" : dataUri);
        }
        if (sb.length() == 0) sb.append("(empty intent)");
        return sb.toString();
    }
}

