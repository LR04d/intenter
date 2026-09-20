package com.open.intenter;

import android.content.ClipData;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Renders an Intent (or a Bundle) as human readable, typed text. Used for
 * received intents, activity results and ordered-broadcast results.
 */
public final class IntentDumper {

    private IntentDumper() {}

    private static final int MAX_STRING = 4000;

    public static String dump(Intent intent) {
        if (intent == null) return "(null intent)";
        StringBuilder sb = new StringBuilder();
        line(sb, "Action", intent.getAction());
        line(sb, "Data", intent.getDataString());
        line(sb, "Type", intent.getType());
        ComponentName cn = intent.getComponent();
        line(sb, "Component", cn == null ? null : cn.flattenToString());
        line(sb, "Package", intent.getPackage());
        Set<String> cats = intent.getCategories();
        line(sb, "Categories", cats == null || cats.isEmpty() ? null : join(cats));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) line(sb, "Identifier", intent.getIdentifier());
        line(sb, "Flags", intent.getFlags() == 0 ? null : FlagRegistry.describe(intent.getFlags()));
        Intent selector = intent.getSelector();
        line(sb, "Selector", selector == null ? null : selector.toUri(Intent.URI_INTENT_SCHEME));

        ClipData clip = intent.getClipData();
        if (clip != null) {
            sb.append("ClipData: ");
            sb.append(clip.getDescription() == null ? "" : clip.getDescription().getLabel());
            sb.append(" [");
            if (clip.getDescription() != null) {
                for (int i = 0; i < clip.getDescription().getMimeTypeCount(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(clip.getDescription().getMimeType(i));
                }
            }
            sb.append("]\n");
            for (int i = 0; i < clip.getItemCount(); i++) {
                ClipData.Item item = clip.getItemAt(i);
                sb.append("  item ").append(i).append(": ");
                if (item.getUri() != null) sb.append("uri=").append(item.getUri());
                else if (item.getIntent() != null) sb.append("intent=").append(item.getIntent().toUri(Intent.URI_INTENT_SCHEME));
                else if (item.getHtmlText() != null) sb.append("html=").append(clip(item.getHtmlText()));
                else sb.append("text=").append(clip(item.getText()));
                sb.append('\n');
            }
        }

        Bundle extras;
        try {
            extras = intent.getExtras();
        } catch (RuntimeException e) {
            sb.append("Extras: <could not unparcel: ").append(e.getClass().getSimpleName())
                    .append(": ").append(e.getMessage()).append(">\n");
            return sb.toString().trim();
        }
        if (extras != null) {
            sb.append("Extras (").append(safeSize(extras)).append("):\n");
            dumpBundle(sb, extras, "  ");
        } else {
            sb.append("Extras: (none)\n");
        }
        return sb.toString().trim();
    }

    /** One-line summary, e.g. "VIEW https://…" or "com.app/.Main". */
    public static String summary(Intent intent) {
        if (intent == null) return "(null)";
        StringBuilder sb = new StringBuilder();
        if (intent.getAction() != null) sb.append(shortAction(intent.getAction()));
        if (intent.getComponent() != null) {
            if (sb.length() > 0) sb.append(" → ");
            sb.append(intent.getComponent().flattenToShortString());
        } else if (intent.getPackage() != null) {
            if (sb.length() > 0) sb.append(" → ");
            sb.append(intent.getPackage());
        }
        if (intent.getData() != null) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(clip(intent.getDataString(), 60));
        }
        if (sb.length() == 0) sb.append("(empty intent)");
        return sb.toString();
    }

    public static String shortAction(String action) {
        if (action == null) return "";
        if (action.startsWith("android.intent.action.")) return action.substring("android.intent.action.".length());
        return action;
    }

    public static String dumpBundle(Bundle b) {
        if (b == null) return "(null)";
        StringBuilder sb = new StringBuilder();
        dumpBundle(sb, b, "");
        return sb.toString().trim();
    }

    private static int safeSize(Bundle b) {
        try {
            return b.size();
        } catch (RuntimeException e) {
            return -1;
        }
    }

    public static void dumpBundle(StringBuilder sb, Bundle b, String indent) {
        Set<String> keys;
        try {
            keys = b.keySet();
        } catch (RuntimeException e) {
            sb.append(indent).append("<could not unparcel: ").append(e.getMessage()).append(">\n");
            return;
        }
        List<String> sorted = new ArrayList<>(keys);
        java.util.Collections.sort(sorted);
        if (sorted.isEmpty()) {
            sb.append(indent).append("(empty)\n");
            return;
        }
        for (String key : sorted) {
            Object v;
            try {
                v = b.get(key);
            } catch (RuntimeException e) {
                sb.append(indent).append(key).append(" = <unreadable: ").append(e.getMessage()).append(">\n");
                continue;
            }
            sb.append(indent).append(key).append(" (").append(typeName(v)).append(") = ");
            if (v instanceof Bundle) {
                sb.append('\n');
                dumpBundle(sb, (Bundle) v, indent + "    ");
            } else {
                sb.append(describeValue(v)).append('\n');
            }
        }
    }

    public static String typeName(Object v) {
        if (v == null) return "null";
        if (v instanceof String) return "String";
        if (v instanceof Integer) return "int";
        if (v instanceof Long) return "long";
        if (v instanceof Boolean) return "boolean";
        if (v instanceof Float) return "float";
        if (v instanceof Double) return "double";
        if (v instanceof Short) return "short";
        if (v instanceof Byte) return "byte";
        if (v instanceof Character) return "char";
        if (v instanceof CharSequence) return "CharSequence:" + v.getClass().getSimpleName();
        if (v instanceof Bundle) return "Bundle";
        if (v instanceof Uri) return "Uri";
        if (v instanceof Intent) return "Intent";
        if (v instanceof ComponentName) return "ComponentName";
        if (v instanceof int[]) return "int[" + ((int[]) v).length + "]";
        if (v instanceof long[]) return "long[" + ((long[]) v).length + "]";
        if (v instanceof boolean[]) return "boolean[" + ((boolean[]) v).length + "]";
        if (v instanceof float[]) return "float[" + ((float[]) v).length + "]";
        if (v instanceof double[]) return "double[" + ((double[]) v).length + "]";
        if (v instanceof short[]) return "short[" + ((short[]) v).length + "]";
        if (v instanceof byte[]) return "byte[" + ((byte[]) v).length + "]";
        if (v instanceof char[]) return "char[" + ((char[]) v).length + "]";
        if (v instanceof String[]) return "String[" + ((String[]) v).length + "]";
        if (v instanceof CharSequence[]) return "CharSequence[" + ((CharSequence[]) v).length + "]";
        if (v instanceof Parcelable[]) return "Parcelable[" + ((Parcelable[]) v).length + "]";
        if (v instanceof ArrayList) {
            ArrayList<?> l = (ArrayList<?>) v;
            String elem = l.isEmpty() || l.get(0) == null ? "?" : l.get(0).getClass().getSimpleName();
            return "ArrayList<" + elem + ">[" + l.size() + "]";
        }
        if (v instanceof Parcelable) return "Parcelable:" + v.getClass().getName();
        if (v instanceof java.io.Serializable) return "Serializable:" + v.getClass().getName();
        return v.getClass().getName();
    }

    public static String describeValue(Object v) {
        if (v == null) return "null";
        if (v instanceof String) return quote((String) v);
        if (v instanceof CharSequence) return quote(v.toString());
        if (v instanceof Intent) return ((Intent) v).toUri(Intent.URI_INTENT_SCHEME);
        if (v instanceof ComponentName) return ((ComponentName) v).flattenToString();
        if (v instanceof int[]) return Arrays.toString((int[]) v);
        if (v instanceof long[]) return Arrays.toString((long[]) v);
        if (v instanceof boolean[]) return Arrays.toString((boolean[]) v);
        if (v instanceof float[]) return Arrays.toString((float[]) v);
        if (v instanceof double[]) return Arrays.toString((double[]) v);
        if (v instanceof short[]) return Arrays.toString((short[]) v);
        if (v instanceof char[]) return quote(new String((char[]) v));
        if (v instanceof byte[]) return hex((byte[]) v);
        if (v instanceof Object[]) {
            Object[] arr = (Object[]) v;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < arr.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(describeValue(arr[i]));
            }
            return sb.append(']').toString();
        }
        if (v instanceof ArrayList) {
            ArrayList<?> l = (ArrayList<?>) v;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(describeValue(l.get(i)));
            }
            return sb.append(']').toString();
        }
        if (v instanceof Bundle) return dumpBundle((Bundle) v);
        return clip(String.valueOf(v));
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder("0x");
        int n = Math.min(bytes.length, 512);
        for (int i = 0; i < n; i++) sb.append(String.format("%02X", bytes[i]));
        if (bytes.length > n) sb.append("… (").append(bytes.length).append(" bytes)");
        String text = new String(bytes, 0, n, java.nio.charset.StandardCharsets.UTF_8);
        boolean printable = true;
        for (char c : text.toCharArray()) {
            if (c < 0x20 && c != '\n' && c != '\t' && c != '\r') { printable = false; break; }
        }
        if (printable && bytes.length > 0) sb.append("  \"").append(clip(text, 200)).append('"');
        return sb.toString();
    }

    private static String quote(String s) {
        return "\"" + clip(s) + "\"";
    }

    private static String clip(CharSequence s) {
        return clip(s, MAX_STRING);
    }

    private static String clip(CharSequence s, int max) {
        if (s == null) return "null";
        String str = s.toString();
        if (str.length() <= max) return str;
        return str.substring(0, max) + "… (" + str.length() + " chars)";
    }

    private static void line(StringBuilder sb, String label, String value) {
        if (value == null || value.isEmpty()) return;
        sb.append(label).append(": ").append(value).append('\n');
    }

    private static String join(Set<String> set) {
        StringBuilder sb = new StringBuilder();
        for (String s : set) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(s);
        }
        return sb.toString();
    }
}
