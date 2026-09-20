package com.open.intenter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Data model representing a fully configured Intent.
 * Serializable to/from JSON for history, favorites, drafts and file export.
 */
public class IntentModel {

    // ─── Launch modes ────────────────────────────────────────────────────

    public static final String MODE_ACTIVITY = "activity";
    public static final String MODE_ACTIVITY_RESULT = "activityResult";
    public static final String MODE_SERVICE = "service";
    public static final String MODE_FG_SERVICE = "fgService";
    public static final String MODE_STOP_SERVICE = "stopService";
    public static final String MODE_BIND_SERVICE = "bindService";
    public static final String MODE_BROADCAST = "broadcast";
    public static final String MODE_ORDERED_BROADCAST = "orderedBroadcast";
    public static final String MODE_RESOLVE = "resolve";

    public static final String[] MODES = {
            MODE_ACTIVITY, MODE_ACTIVITY_RESULT, MODE_SERVICE, MODE_FG_SERVICE,
            MODE_STOP_SERVICE, MODE_BIND_SERVICE, MODE_BROADCAST, MODE_ORDERED_BROADCAST, MODE_RESOLVE
    };

    public static String modeLabel(String mode) {
        if (mode == null) return "Activity";
        switch (mode) {
            case MODE_ACTIVITY_RESULT:    return "Activity for result";
            case MODE_SERVICE:            return "Start service";
            case MODE_FG_SERVICE:         return "Foreground service";
            case MODE_STOP_SERVICE:       return "Stop service";
            case MODE_BIND_SERVICE:       return "Bind service";
            case MODE_BROADCAST:          return "Broadcast";
            case MODE_ORDERED_BROADCAST:  return "Ordered broadcast";
            case MODE_RESOLVE:            return "Resolve only (dry run)";
            default:                      return "Activity";
        }
    }

    public static String modeShortLabel(String mode) {
        if (mode == null) return "Activity";
        switch (mode) {
            case MODE_ACTIVITY_RESULT:    return "For result";
            case MODE_SERVICE:            return "Service";
            case MODE_FG_SERVICE:         return "FG service";
            case MODE_STOP_SERVICE:       return "Stop svc";
            case MODE_BIND_SERVICE:       return "Bind";
            case MODE_BROADCAST:          return "Broadcast";
            case MODE_ORDERED_BROADCAST:  return "Ordered";
            case MODE_RESOLVE:            return "Resolve";
            default:                      return "Activity";
        }
    }

    public static boolean isActivityMode(String mode) {
        return MODE_ACTIVITY.equals(mode) || MODE_ACTIVITY_RESULT.equals(mode);
    }

    public static boolean isServiceMode(String mode) {
        return MODE_SERVICE.equals(mode) || MODE_FG_SERVICE.equals(mode)
                || MODE_STOP_SERVICE.equals(mode) || MODE_BIND_SERVICE.equals(mode);
    }

    public static boolean isBroadcastMode(String mode) {
        return MODE_BROADCAST.equals(mode) || MODE_ORDERED_BROADCAST.equals(mode);
    }

    /** Maps legacy chip ids (chipActivity…) to the new mode keys. */
    public static String normalizeMode(String raw) {
        if (raw == null || raw.isEmpty()) return MODE_ACTIVITY;
        switch (raw) {
            case "chipActivity":       return MODE_ACTIVITY;
            case "chipService":        return MODE_SERVICE;
            case "chipFgService":      return MODE_FG_SERVICE;
            case "chipBroadcast":      return MODE_BROADCAST;
            case "chipActivityResult": return MODE_ACTIVITY_RESULT;
            default:
                for (String m : MODES) if (m.equals(raw)) return raw;
                return MODE_ACTIVITY;
        }
    }

    // ─── Fields ──────────────────────────────────────────────────────────

    public String label = "";          // auto-generated summary for lists
    public String name = "";           // user-given name (favorites / presets)
    public String description = "";    // optional note (presets)
    public long timestamp;             // millis

    // Component
    public boolean useComponent;
    public String packageName = "";
    public String componentName = "";

    // Action(s)
    public boolean useAction;
    public String action = "";
    public List<String> actions = new ArrayList<>();

    // Data
    public boolean useData;
    public String dataUri = "";
    public String mimeType = "";

    // Categories
    public boolean useCategory;
    public List<String> categories = new ArrayList<>();

    // Extras (nested bundles are ExtraEntry with type Bundle and children)
    public boolean useExtras;
    public List<ExtraEntry> extras = new ArrayList<>();

    // Flags
    public boolean useFlags;
    public List<String> flagNames = new ArrayList<>();
    public String customFlags = "";

    // Launch mode
    public String launchType = MODE_ACTIVITY;

    // Chooser
    public boolean useChooser;
    public String chooserTitle = "";

    // Advanced
    public boolean useAdvanced;
    public String permission = "";     // receiver permission for broadcasts
    public String identifier = "";     // Intent.setIdentifier (API 29+)

    // Clip Data
    public boolean useClipData;
    public String clipDataLabel = "";
    public List<String> clipDataMimeTypes = new ArrayList<>();
    public List<ClipDataItem> clipDataItems = new ArrayList<>();

    // ─── Nested types ────────────────────────────────────────────────────

    public static class ClipDataItem {
        public String type = "Text"; // Text, Html, Uri, Intent
        public String value = "";

        public ClipDataItem() {}
        public ClipDataItem(String type, String value) {
            this.type = type;
            this.value = value;
        }

        public JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("type", type);
            o.put("value", value);
            return o;
        }

        public static ClipDataItem fromJson(JSONObject o) {
            ClipDataItem item = new ClipDataItem();
            item.type = o.optString("type", "Text");
            item.value = o.optString("value", "");
            return item;
        }
    }

    public static class ExtraEntry {
        public String key = "";
        public String value = "";
        public String type = ExtraTypes.STRING;
        public List<ExtraEntry> children = new ArrayList<>();   // only for Bundle

        public ExtraEntry() {}
        public ExtraEntry(String key, String value, String type) {
            this.key = key;
            this.value = value;
            this.type = type;
        }

        public static ExtraEntry bundle(String key, List<ExtraEntry> children) {
            ExtraEntry e = new ExtraEntry(key, "", ExtraTypes.BUNDLE);
            e.children.addAll(children);
            return e;
        }

        public ExtraEntry copy() {
            ExtraEntry e = new ExtraEntry(key, value, type);
            for (ExtraEntry c : children) e.children.add(c.copy());
            return e;
        }

        public JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("key", key);
            o.put("value", value);
            o.put("type", type);
            if (!children.isEmpty()) {
                JSONArray arr = new JSONArray();
                for (ExtraEntry c : children) arr.put(c.toJson());
                o.put("children", arr);
            }
            return o;
        }

        public static ExtraEntry fromJson(JSONObject o) throws JSONException {
            ExtraEntry e = new ExtraEntry();
            e.key = o.optString("key", "");
            e.value = o.optString("value", "");
            e.type = o.optString("type", ExtraTypes.STRING);
            JSONArray arr = o.optJSONArray("children");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) e.children.add(fromJson(arr.getJSONObject(i)));
            }
            return e;
        }
    }

    // ─── Serialization ───────────────────────────────────────────────────

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("version", 2);
        o.put("label", label);
        o.put("name", name);
        o.put("description", description);
        o.put("timestamp", timestamp);

        o.put("useComponent", useComponent);
        o.put("packageName", packageName);
        o.put("componentName", componentName);

        o.put("useAction", useAction);
        o.put("action", actions.isEmpty() ? action : actions.get(0));
        o.put("actions", toArray(actions));

        o.put("useData", useData);
        o.put("dataUri", dataUri);
        o.put("mimeType", mimeType);

        o.put("useCategory", useCategory);
        o.put("categories", toArray(categories));

        o.put("useExtras", useExtras);
        JSONArray extArr = new JSONArray();
        for (ExtraEntry e : extras) extArr.put(e.toJson());
        o.put("extras", extArr);

        o.put("useFlags", useFlags);
        o.put("flagNames", toArray(flagNames));
        o.put("customFlags", customFlags);

        o.put("launchType", launchType);

        o.put("useChooser", useChooser);
        o.put("chooserTitle", chooserTitle);

        o.put("useAdvanced", useAdvanced);
        o.put("permission", permission);
        o.put("identifier", identifier);

        o.put("useClipData", useClipData);
        o.put("clipDataLabel", clipDataLabel);
        o.put("clipDataMimeTypes", toArray(clipDataMimeTypes));
        JSONArray cdItems = new JSONArray();
        for (ClipDataItem item : clipDataItems) cdItems.put(item.toJson());
        o.put("clipDataItems", cdItems);

        return o;
    }

    public String toJsonString() {
        try {
            return toJson().toString(2);
        } catch (JSONException e) {
            return "{}";
        }
    }

    public static IntentModel fromJsonString(String json) throws JSONException {
        return fromJson(new JSONObject(json));
    }

    public static IntentModel fromJson(JSONObject o) throws JSONException {
        IntentModel m = new IntentModel();
        m.label = o.optString("label", "");
        m.name = o.optString("name", "");
        m.description = o.optString("description", "");
        m.timestamp = o.optLong("timestamp", 0);

        m.useComponent = o.optBoolean("useComponent", false);
        m.packageName = o.optString("packageName", "");
        m.componentName = o.optString("componentName", "");

        m.useAction = o.optBoolean("useAction", false);
        m.action = o.optString("action", "");
        m.actions.addAll(fromArray(o.optJSONArray("actions")));
        if (m.actions.isEmpty() && !m.action.isEmpty()) m.actions.add(m.action);

        m.useData = o.optBoolean("useData", false);
        m.dataUri = o.optString("dataUri", "");
        m.mimeType = o.optString("mimeType", "");

        m.useCategory = o.optBoolean("useCategory", false);
        m.categories.addAll(fromArray(o.optJSONArray("categories")));

        m.useExtras = o.optBoolean("useExtras", false);
        JSONArray extArr = o.optJSONArray("extras");
        if (extArr != null) {
            for (int i = 0; i < extArr.length(); i++) m.extras.add(ExtraEntry.fromJson(extArr.getJSONObject(i)));
        }

        // Legacy v1 "bundles" section: migrate into nested Bundle extras.
        JSONArray bunArr = o.optJSONArray("bundles");
        if (bunArr != null && bunArr.length() > 0 && o.optBoolean("useBundle", false)) {
            m.useExtras = true;
            for (int i = 0; i < bunArr.length(); i++) {
                JSONObject b = bunArr.getJSONObject(i);
                String key = b.optString("key", "");
                List<ExtraEntry> children = new ArrayList<>();
                JSONArray arr = b.optJSONArray("extras");
                if (arr != null) {
                    for (int j = 0; j < arr.length(); j++) children.add(ExtraEntry.fromJson(arr.getJSONObject(j)));
                }
                if (key.isEmpty()) m.extras.addAll(children);
                else m.extras.add(ExtraEntry.bundle(key, children));
            }
        }

        m.useFlags = o.optBoolean("useFlags", false);
        m.flagNames.addAll(fromArray(o.optJSONArray("flagNames")));
        m.customFlags = o.optString("customFlags", "");

        m.launchType = normalizeMode(o.optString("launchType", MODE_ACTIVITY));

        m.useChooser = o.optBoolean("useChooser", false);
        m.chooserTitle = o.optString("chooserTitle", "");

        m.useAdvanced = o.optBoolean("useAdvanced", false);
        m.permission = o.optString("permission", "");
        m.identifier = o.optString("identifier", "");
        if (!m.permission.isEmpty() || !m.identifier.isEmpty()) m.useAdvanced = true;

        m.useClipData = o.optBoolean("useClipData", false);
        m.clipDataLabel = o.optString("clipDataLabel", "");
        m.clipDataMimeTypes.addAll(fromArray(o.optJSONArray("clipDataMimeTypes")));
        JSONArray cdItems = o.optJSONArray("clipDataItems");
        if (cdItems != null) {
            for (int i = 0; i < cdItems.length(); i++) m.clipDataItems.add(ClipDataItem.fromJson(cdItems.getJSONObject(i)));
        }

        return m;
    }

    private static JSONArray toArray(List<String> list) {
        JSONArray arr = new JSONArray();
        for (String s : list) arr.put(s);
        return arr;
    }

    private static List<String> fromArray(JSONArray arr) throws JSONException {
        List<String> list = new ArrayList<>();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) list.add(arr.getString(i));
        }
        return list;
    }

    public IntentModel copy() {
        try {
            IntentModel c = fromJson(toJson());
            return c;
        } catch (JSONException e) {
            return new IntentModel();
        }
    }

    /** First non-empty action, or empty string. */
    public String primaryAction() {
        for (String a : actions) if (a != null && !a.trim().isEmpty()) return a.trim();
        return action == null ? "" : action.trim();
    }

    /** Number of extras including nested ones. */
    public int extraCount() {
        return countExtras(extras);
    }

    private static int countExtras(List<ExtraEntry> list) {
        int n = 0;
        for (ExtraEntry e : list) {
            n++;
            if (!e.children.isEmpty()) n += countExtras(e.children);
        }
        return n;
    }

    /** Generate a human-readable label for lists. */
    public String generateLabel() {
        StringBuilder sb = new StringBuilder();
        String primaryAction = primaryAction();
        if (!primaryAction.isEmpty()) {
            String shortAction = primaryAction;
            if (shortAction.startsWith("android.intent.action.")) {
                shortAction = shortAction.substring("android.intent.action.".length());
            }
            sb.append(shortAction);
            int extra = 0;
            for (String a : actions) if (a != null && !a.trim().isEmpty()) extra++;
            if (extra > 1) sb.append(" (+").append(extra - 1).append(")");
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
