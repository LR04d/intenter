package com.open.intenter;

import android.content.ClipData;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;

import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts an {@link IntentModel} into real {@link Intent} objects.
 * All value parsing lives here so it can be unit tested and reused by the
 * provider screen (ContentValues) and the code exporter.
 */
public final class IntentBuilder {

    private IntentBuilder() {}

    /** Thrown when a field value can't be converted to the requested type. */
    public static class BuildException extends IllegalArgumentException {
        public BuildException(String message) { super(message); }
    }

    // ─── Public API ──────────────────────────────────────────────────────

    /** Builds one intent per action (or a single intent when no action is set). */
    public static List<Intent> build(IntentModel m) {
        List<Intent> intents = new ArrayList<>();
        List<String> activeActions = new ArrayList<>();
        if (m.useAction) {
            for (String a : m.actions) {
                if (a != null && !a.trim().isEmpty()) activeActions.add(a.trim());
            }
        }
        if (activeActions.isEmpty()) {
            intents.add(buildOne(m, null));
        } else {
            for (String action : activeActions) intents.add(buildOne(m, action));
        }
        return intents;
    }

    public static Intent buildFirst(IntentModel m) {
        List<Intent> list = build(m);
        return list.isEmpty() ? new Intent() : list.get(0);
    }

    public static Intent buildOne(IntentModel m, String action) {
        Intent intent = new Intent();
        if (action != null && !action.isEmpty()) intent.setAction(action);

        if (m.useComponent) {
            String pkg = trim(m.packageName);
            String cls = trim(m.componentName);
            if (!pkg.isEmpty() && !cls.isEmpty()) {
                if (cls.startsWith(".")) cls = pkg + cls;
                intent.setComponent(new ComponentName(pkg, cls));
            } else if (!pkg.isEmpty()) {
                intent.setPackage(pkg);
            } else if (!cls.isEmpty()) {
                ComponentName cn = ComponentName.unflattenFromString(cls);
                if (cn == null) throw new BuildException("Component must be pkg/cls when package is empty");
                intent.setComponent(cn);
            }
        }

        if (m.useData) {
            String uri = trim(m.dataUri);
            String mime = trim(m.mimeType);
            if (!uri.isEmpty() && !mime.isEmpty()) intent.setDataAndType(Uri.parse(uri), mime);
            else if (!uri.isEmpty()) intent.setData(Uri.parse(uri));
            else if (!mime.isEmpty()) intent.setType(mime);
        }

        if (m.useCategory) {
            for (String c : m.categories) {
                if (c != null && !c.trim().isEmpty()) intent.addCategory(c.trim());
            }
        }

        if (m.useClipData) {
            ClipData clip = buildClipData(m);
            if (clip != null) intent.setClipData(clip);
        }

        if (m.useExtras && !m.extras.isEmpty()) {
            Bundle extras = buildBundle(m.extras);
            if (!extras.isEmpty()) intent.putExtras(extras);
        }

        if (m.useFlags) {
            int flags = FlagRegistry.combine(m.flagNames, m.customFlags);
            if (flags != 0) intent.addFlags(flags);
        }

        if (m.useAdvanced) {
            String id = trim(m.identifier);
            if (!id.isEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) intent.setIdentifier(id);
        }

        return intent;
    }

    /** Effective flags value for display. */
    public static int flagsOf(IntentModel m) {
        return m.useFlags ? FlagRegistry.combine(m.flagNames, m.customFlags) : 0;
    }

    /** True when nothing meaningful is set on the intent. */
    public static boolean isBlank(Intent intent) {
        return intent.getComponent() == null
                && intent.getPackage() == null
                && intent.getAction() == null
                && intent.getData() == null
                && intent.getType() == null
                && intent.getCategories() == null
                && intent.getClipData() == null
                && (intent.getExtras() == null || intent.getExtras().isEmpty())
                && intent.getFlags() == 0;
    }

    // ─── Clip data ───────────────────────────────────────────────────────

    public static ClipData buildClipData(IntentModel m) {
        if (m.clipDataItems.isEmpty()) return null;
        String label = trim(m.clipDataLabel);
        if (label.isEmpty()) label = "Intenter ClipData";
        List<String> mimeList = new ArrayList<>();
        for (String s : m.clipDataMimeTypes) if (s != null && !s.trim().isEmpty()) mimeList.add(s.trim());
        if (mimeList.isEmpty()) mimeList.add("text/plain");
        String[] mimes = mimeList.toArray(new String[0]);

        ClipData clip = null;
        for (IntentModel.ClipDataItem row : m.clipDataItems) {
            String val = row.value == null ? "" : row.value;
            if (val.isEmpty()) continue;
            ClipData.Item item;
            switch (row.type == null ? "Text" : row.type) {
                case "Uri":    item = new ClipData.Item(Uri.parse(val)); break;
                case "Html":   item = new ClipData.Item(val, val); break;
                case "Intent": item = new ClipData.Item(parseIntentValue(val)); break;
                default:       item = new ClipData.Item(val); break;
            }
            if (clip == null) clip = new ClipData(label, mimes, item);
            else clip.addItem(item);
        }
        return clip;
    }

    // ─── Extras ──────────────────────────────────────────────────────────

    /** Builds a Bundle from a list of entries (recursively). */
    public static Bundle buildBundle(List<IntentModel.ExtraEntry> entries) {
        Bundle b = new Bundle();
        for (IntentModel.ExtraEntry e : entries) putExtra(b, e);
        return b;
    }

    /** Puts a single typed entry into the bundle. Entries with empty keys are skipped. */
    public static void putExtra(Bundle b, IntentModel.ExtraEntry e) {
        String key = e.key == null ? "" : e.key;
        if (key.isEmpty()) return;
        String type = e.type == null ? ExtraTypes.STRING : e.type;
        String v = e.value == null ? "" : e.value;
        try {
            switch (type) {
                case ExtraTypes.STRING:        b.putString(key, v); break;
                case ExtraTypes.CHAR_SEQUENCE: b.putCharSequence(key, v); break;
                case ExtraTypes.INT:           b.putInt(key, parseInt(v)); break;
                case ExtraTypes.LONG:          b.putLong(key, parseLong(v)); break;
                case ExtraTypes.SHORT:         b.putShort(key, (short) parseInt(v)); break;
                case ExtraTypes.BYTE:          b.putByte(key, (byte) parseInt(v)); break;
                case ExtraTypes.CHAR:          b.putChar(key, parseChar(v)); break;
                case ExtraTypes.BOOLEAN:       b.putBoolean(key, parseBoolean(v)); break;
                case ExtraTypes.FLOAT:         b.putFloat(key, Float.parseFloat(v.trim())); break;
                case ExtraTypes.DOUBLE:        b.putDouble(key, Double.parseDouble(v.trim())); break;
                case ExtraTypes.URI:           b.putParcelable(key, Uri.parse(v.trim())); break;
                case ExtraTypes.COMPONENT_NAME: b.putParcelable(key, parseComponent(v)); break;
                case ExtraTypes.INTENT:        b.putParcelable(key, parseIntentValue(v)); break;
                case ExtraTypes.BUNDLE:        b.putBundle(key, buildBundle(e.children)); break;
                case ExtraTypes.NULL:          b.putString(key, null); break;
                case ExtraTypes.STRING_ARRAY:  b.putStringArray(key, splitStrings(v)); break;
                case ExtraTypes.CHAR_SEQUENCE_ARRAY: {
                    String[] parts = splitStrings(v);
                    CharSequence[] cs = new CharSequence[parts.length];
                    System.arraycopy(parts, 0, cs, 0, parts.length);
                    b.putCharSequenceArray(key, cs);
                    break;
                }
                case ExtraTypes.INT_ARRAY: {
                    String[] parts = splitTrimmed(v);
                    int[] arr = new int[parts.length];
                    for (int i = 0; i < parts.length; i++) arr[i] = parseInt(parts[i]);
                    b.putIntArray(key, arr);
                    break;
                }
                case ExtraTypes.LONG_ARRAY: {
                    String[] parts = splitTrimmed(v);
                    long[] arr = new long[parts.length];
                    for (int i = 0; i < parts.length; i++) arr[i] = parseLong(parts[i]);
                    b.putLongArray(key, arr);
                    break;
                }
                case ExtraTypes.SHORT_ARRAY: {
                    String[] parts = splitTrimmed(v);
                    short[] arr = new short[parts.length];
                    for (int i = 0; i < parts.length; i++) arr[i] = (short) parseInt(parts[i]);
                    b.putShortArray(key, arr);
                    break;
                }
                case ExtraTypes.BYTE_ARRAY:    b.putByteArray(key, parseBytes(v)); break;
                case ExtraTypes.CHAR_ARRAY:    b.putCharArray(key, v.toCharArray()); break;
                case ExtraTypes.BOOLEAN_ARRAY: {
                    String[] parts = splitTrimmed(v);
                    boolean[] arr = new boolean[parts.length];
                    for (int i = 0; i < parts.length; i++) arr[i] = parseBoolean(parts[i]);
                    b.putBooleanArray(key, arr);
                    break;
                }
                case ExtraTypes.FLOAT_ARRAY: {
                    String[] parts = splitTrimmed(v);
                    float[] arr = new float[parts.length];
                    for (int i = 0; i < parts.length; i++) arr[i] = Float.parseFloat(parts[i]);
                    b.putFloatArray(key, arr);
                    break;
                }
                case ExtraTypes.DOUBLE_ARRAY: {
                    String[] parts = splitTrimmed(v);
                    double[] arr = new double[parts.length];
                    for (int i = 0; i < parts.length; i++) arr[i] = Double.parseDouble(parts[i]);
                    b.putDoubleArray(key, arr);
                    break;
                }
                case ExtraTypes.URI_ARRAY: {
                    String[] parts = splitTrimmed(v);
                    Parcelable[] arr = new Parcelable[parts.length];
                    for (int i = 0; i < parts.length; i++) arr[i] = Uri.parse(parts[i]);
                    b.putParcelableArray(key, arr);
                    break;
                }
                case ExtraTypes.STRING_LIST: {
                    ArrayList<String> list = new ArrayList<>();
                    for (String s : splitStrings(v)) list.add(s);
                    b.putStringArrayList(key, list);
                    break;
                }
                case ExtraTypes.INT_LIST: {
                    ArrayList<Integer> list = new ArrayList<>();
                    for (String s : splitTrimmed(v)) list.add(parseInt(s));
                    b.putIntegerArrayList(key, list);
                    break;
                }
                case ExtraTypes.CHAR_SEQUENCE_LIST: {
                    ArrayList<CharSequence> list = new ArrayList<>();
                    for (String s : splitStrings(v)) list.add(s);
                    b.putCharSequenceArrayList(key, list);
                    break;
                }
                case ExtraTypes.URI_LIST: {
                    ArrayList<Parcelable> list = new ArrayList<>();
                    for (String s : splitTrimmed(v)) list.add(Uri.parse(s));
                    b.putParcelableArrayList(key, list);
                    break;
                }
                default:
                    b.putString(key, v);
            }
        } catch (NumberFormatException ex) {
            throw new BuildException("Extra \"" + key + "\" (" + type + "): invalid value \"" + v + "\"");
        }
    }

    // ─── Parsing helpers (public for reuse + tests) ──────────────────────

    public static int parseInt(String s) {
        String t = s.trim();
        try {
            return Integer.parseInt(t);
        } catch (NumberFormatException e) {
            return Integer.decode(t);
        }
    }

    public static long parseLong(String s) {
        String t = s.trim();
        try {
            return Long.parseLong(t);
        } catch (NumberFormatException e) {
            return Long.decode(t);
        }
    }

    public static boolean parseBoolean(String s) {
        String t = s.trim().toLowerCase();
        return t.equals("true") || t.equals("1") || t.equals("yes") || t.equals("y") || t.equals("on");
    }

    public static char parseChar(String s) {
        if (s.isEmpty()) throw new NumberFormatException("empty char");
        if (s.length() == 6 && s.startsWith("\\u")) {
            return (char) Integer.parseInt(s.substring(2), 16);
        }
        return s.charAt(0);
    }

    public static ComponentName parseComponent(String s) {
        ComponentName cn = ComponentName.unflattenFromString(s.trim());
        if (cn == null) throw new NumberFormatException("component must be pkg/cls");
        return cn;
    }

    /** Parses an intent: / android-app: URI, or any other URI as a VIEW intent. */
    public static Intent parseIntentValue(String s) {
        try {
            return Intent.parseUri(s.trim(),
                    Intent.URI_INTENT_SCHEME | Intent.URI_ANDROID_APP_SCHEME | Intent.URI_ALLOW_UNSAFE);
        } catch (URISyntaxException e) {
            throw new NumberFormatException("bad intent uri: " + e.getMessage());
        }
    }

    /**
     * byte[] syntax: "0xDEADBEEF" / "hex:DEAD" (hex), "utf8:text" (encoded text),
     * otherwise comma-separated integers (-128..255).
     */
    public static byte[] parseBytes(String s) {
        String t = s.trim();
        if (t.isEmpty()) return new byte[0];
        String hex = null;
        if (t.startsWith("0x") || t.startsWith("0X")) hex = t.substring(2);
        else if (t.startsWith("hex:")) hex = t.substring(4);
        if (hex != null) {
            hex = hex.replaceAll("[\\s:]", "");
            if (hex.length() % 2 != 0) throw new NumberFormatException("odd hex length");
            byte[] out = new byte[hex.length() / 2];
            for (int i = 0; i < out.length; i++) {
                out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            }
            return out;
        }
        if (t.startsWith("utf8:")) return t.substring(5).getBytes(StandardCharsets.UTF_8);
        String[] parts = splitTrimmed(t);
        byte[] out = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) {
            int v = parseInt(parts[i]);
            if (v < -128 || v > 255) throw new NumberFormatException("byte out of range: " + v);
            out[i] = (byte) v;
        }
        return out;
    }

    /** Splits on commas, trims each part; an empty input yields an empty array. */
    public static String[] splitTrimmed(String s) {
        if (s == null || s.trim().isEmpty()) return new String[0];
        String[] parts = s.split(",", -1);
        for (int i = 0; i < parts.length; i++) parts[i] = parts[i].trim();
        return parts;
    }

    /** Splits on commas without trimming; an empty input yields an empty array. */
    public static String[] splitStrings(String s) {
        if (s == null || s.isEmpty()) return new String[0];
        return s.split(",", -1);
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
