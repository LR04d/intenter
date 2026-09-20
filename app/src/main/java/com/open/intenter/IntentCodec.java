package com.open.intenter;

import android.content.ClipData;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Import / export between {@link IntentModel} and other representations:
 * intent URIs, {@code adb shell am} commands, Java / Kotlin source and JSON.
 */
public final class IntentCodec {

    private IntentCodec() {}

    public enum Format { URI, ADB, JAVA, KOTLIN, JSON }

    // ══════════════════════════════════════════════════════════════════════
    //  Export
    // ══════════════════════════════════════════════════════════════════════

    public static String export(Format format, IntentModel model, List<Intent> intents) {
        switch (format) {
            case ADB:    return toAdb(model, intents);
            case JAVA:   return toJava(model);
            case KOTLIN: return toKotlin(model);
            case JSON:   return model.toJsonString();
            case URI:
            default:     return toUris(intents);
        }
    }

    public static String toUris(List<Intent> intents) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < intents.size(); i++) {
            if (i > 0) sb.append("\n\n");
            Intent intent = intents.get(i);
            sb.append(intent.toUri(Intent.URI_INTENT_SCHEME));
            for (String note : uriNotes(intent)) sb.append("\n# note: ").append(note);
        }
        return sb.toString();
    }

    /** Parts of an intent that Intent.toUri() cannot encode. */
    public static List<String> uriNotes(Intent intent) {
        List<String> notes = new ArrayList<>();
        Bundle extras = null;
        try { extras = intent.getExtras(); } catch (RuntimeException ignored) {}
        if (extras != null) {
            List<String> keys = new ArrayList<>(extras.keySet());
            java.util.Collections.sort(keys);
            for (String key : keys) {
                Object v = extras.get(key);
                boolean scalar = v instanceof String || v instanceof Integer || v instanceof Long
                        || v instanceof Boolean || v instanceof Float || v instanceof Double
                        || v instanceof Byte || v instanceof Short || v instanceof Character;
                if (!scalar) notes.add("extra \"" + key + "\" (" + IntentDumper.typeName(v) + ") is not representable in an intent URI");
            }
        }
        if (intent.getClipData() != null) notes.add("ClipData is not representable in an intent URI");
        return notes;
    }

    // ─── adb shell am ────────────────────────────────────────────────────

    public static String toAdb(IntentModel model, List<Intent> intents) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < intents.size(); i++) {
            if (i > 0) sb.append("\n\n");
            sb.append(toAdb(model, intents.get(i)));
        }
        return sb.toString();
    }

    public static String toAdb(IntentModel model, Intent intent) {
        StringBuilder sb = new StringBuilder();
        List<String> notes = new ArrayList<>();
        String mode = model.launchType;
        String verb;
        switch (mode) {
            case IntentModel.MODE_SERVICE:      verb = "am startservice"; break;
            case IntentModel.MODE_FG_SERVICE:   verb = "am start-foreground-service"; break;
            case IntentModel.MODE_STOP_SERVICE: verb = "am stopservice"; break;
            case IntentModel.MODE_BROADCAST:
            case IntentModel.MODE_ORDERED_BROADCAST: verb = "am broadcast"; break;
            case IntentModel.MODE_BIND_SERVICE:
                verb = "am startservice";
                notes.add("am cannot bind services; startservice shown instead");
                break;
            case IntentModel.MODE_RESOLVE:
                verb = "cmd package query-activities --brief";
                break;
            case IntentModel.MODE_ACTIVITY_RESULT:
                verb = "am start -W";
                notes.add("adb cannot receive the activity result");
                break;
            default: verb = "am start"; break;
        }
        sb.append("adb shell ").append(verb);
        if (IntentModel.isBroadcastMode(mode) && model.useAdvanced && !model.permission.trim().isEmpty()) {
            sb.append(" --receiver-permission ").append(shellQuote(model.permission.trim()));
        }
        if (intent.getAction() != null) sb.append(" -a ").append(shellQuote(intent.getAction()));
        if (intent.getDataString() != null) sb.append(" -d ").append(shellQuote(intent.getDataString()));
        if (intent.getType() != null) sb.append(" -t ").append(shellQuote(intent.getType()));
        Set<String> cats = intent.getCategories();
        if (cats != null) for (String c : cats) sb.append(" -c ").append(shellQuote(c));
        if (intent.getComponent() != null) {
            sb.append(" -n ").append(shellQuote(intent.getComponent().flattenToShortString()));
        } else if (intent.getPackage() != null) {
            sb.append(" -p ").append(shellQuote(intent.getPackage()));
        }
        if (intent.getFlags() != 0) sb.append(" -f 0x").append(Integer.toHexString(intent.getFlags()));
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && intent.getIdentifier() != null) {
            sb.append(" -i ").append(shellQuote(intent.getIdentifier()));
        }

        Bundle extras = intent.getExtras();
        if (extras != null) {
            List<String> keys = new ArrayList<>(extras.keySet());
            java.util.Collections.sort(keys);
            for (String key : keys) appendAmExtra(sb, notes, key, extras.get(key));
        }
        if (intent.getClipData() != null) notes.add("ClipData cannot be expressed with am");
        if (model.useChooser) notes.add("chooser wrapping is not supported by am");
        for (String n : notes) sb.append("\n# note: ").append(n);
        return sb.toString();
    }

    private static void appendAmExtra(StringBuilder sb, List<String> notes, String key, Object v) {
        String k = shellQuote(key);
        if (v == null) { sb.append(" --esn ").append(k); return; }
        if (v instanceof String) { sb.append(" --es ").append(k).append(' ').append(shellQuote((String) v)); return; }
        if (v instanceof Integer) { sb.append(" --ei ").append(k).append(' ').append(v); return; }
        if (v instanceof Long) { sb.append(" --el ").append(k).append(' ').append(v); return; }
        if (v instanceof Float) { sb.append(" --ef ").append(k).append(' ').append(v); return; }
        if (v instanceof Double) { sb.append(" --ed ").append(k).append(' ').append(v); return; }
        if (v instanceof Boolean) { sb.append(" --ez ").append(k).append(' ').append(v); return; }
        if (v instanceof Uri) { sb.append(" --eu ").append(k).append(' ').append(shellQuote(v.toString())); return; }
        if (v instanceof ComponentName) { sb.append(" --ecn ").append(k).append(' ').append(shellQuote(((ComponentName) v).flattenToString())); return; }
        if (v instanceof int[]) { sb.append(" --eia ").append(k).append(' ').append(joinInts((int[]) v)); return; }
        if (v instanceof long[]) { sb.append(" --ela ").append(k).append(' ').append(joinLongs((long[]) v)); return; }
        if (v instanceof float[]) { sb.append(" --efa ").append(k).append(' ').append(joinFloats((float[]) v)); return; }
        if (v instanceof double[]) { sb.append(" --eda ").append(k).append(' ').append(joinDoubles((double[]) v)); return; }
        if (v instanceof String[]) { sb.append(" --esa ").append(k).append(' ').append(shellQuote(joinStrings((String[]) v))); return; }
        if (v instanceof ArrayList) {
            ArrayList<?> list = (ArrayList<?>) v;
            Object first = list.isEmpty() ? null : list.get(0);
            if (first instanceof Integer) { sb.append(" --eial ").append(k).append(' ').append(joinList(list)); return; }
            if (first instanceof Long) { sb.append(" --elal ").append(k).append(' ').append(joinList(list)); return; }
            if (first instanceof Float) { sb.append(" --efal ").append(k).append(' ').append(joinList(list)); return; }
            if (first instanceof Double) { sb.append(" --edal ").append(k).append(' ').append(joinList(list)); return; }
            if (first instanceof String || list.isEmpty()) { sb.append(" --esal ").append(k).append(' ').append(shellQuote(joinList(list))); return; }
        }
        notes.add("extra \"" + key + "\" (" + IntentDumper.typeName(v) + ") cannot be expressed with am");
    }

    /** Quotes for a POSIX shell when needed. */
    public static String shellQuote(String s) {
        if (s == null) return "''";
        if (!s.isEmpty() && s.matches("[A-Za-z0-9_./:@%+=,-]+")) return s;
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private static String joinInts(int[] a) { StringBuilder sb = new StringBuilder(); for (int i = 0; i < a.length; i++) { if (i > 0) sb.append(','); sb.append(a[i]); } return sb.toString(); }
    private static String joinLongs(long[] a) { StringBuilder sb = new StringBuilder(); for (int i = 0; i < a.length; i++) { if (i > 0) sb.append(','); sb.append(a[i]); } return sb.toString(); }
    private static String joinFloats(float[] a) { StringBuilder sb = new StringBuilder(); for (int i = 0; i < a.length; i++) { if (i > 0) sb.append(','); sb.append(a[i]); } return sb.toString(); }
    private static String joinDoubles(double[] a) { StringBuilder sb = new StringBuilder(); for (int i = 0; i < a.length; i++) { if (i > 0) sb.append(','); sb.append(a[i]); } return sb.toString(); }
    private static String joinStrings(String[] a) { StringBuilder sb = new StringBuilder(); for (int i = 0; i < a.length; i++) { if (i > 0) sb.append(','); sb.append(a[i] == null ? "" : a[i].replace(",", "\\,")); } return sb.toString(); }
    private static String joinList(ArrayList<?> a) { StringBuilder sb = new StringBuilder(); for (int i = 0; i < a.size(); i++) { if (i > 0) sb.append(','); sb.append(a.get(i)); } return sb.toString(); }

    // ─── Java / Kotlin ───────────────────────────────────────────────────

    public static String toJava(IntentModel m) {
        return toCode(m, false);
    }

    public static String toKotlin(IntentModel m) {
        return toCode(m, true);
    }

    private static String toCode(IntentModel m, boolean kotlin) {
        StringBuilder sb = new StringBuilder();
        String eol = kotlin ? "\n" : ";\n";
        String action = m.useAction ? m.primaryAction() : "";
        List<String> extraActions = new ArrayList<>();
        if (m.useAction) {
            boolean first = true;
            for (String a : m.actions) {
                if (a == null || a.trim().isEmpty()) continue;
                if (first) { first = false; continue; }
                extraActions.add(a.trim());
            }
        }
        if (kotlin) {
            sb.append("val intent = Intent(").append(action.isEmpty() ? "" : lit(kotlin, action)).append(")\n");
        } else {
            sb.append("Intent intent = new Intent(").append(action.isEmpty() ? "" : lit(kotlin, action)).append(");\n");
        }
        if (m.useComponent) {
            String pkg = m.packageName.trim();
            String cls = m.componentName.trim();
            if (!pkg.isEmpty() && !cls.isEmpty()) {
                if (cls.startsWith(".")) cls = pkg + cls;
                sb.append("intent.setClassName(").append(lit(kotlin, pkg)).append(", ").append(lit(kotlin, cls)).append(")").append(eol);
            } else if (!pkg.isEmpty()) {
                sb.append("intent.setPackage(").append(lit(kotlin, pkg)).append(")").append(eol);
            }
        }
        if (m.useData) {
            String uri = m.dataUri.trim();
            String mime = m.mimeType.trim();
            if (!uri.isEmpty() && !mime.isEmpty()) {
                sb.append("intent.setDataAndType(Uri.parse(").append(lit(kotlin, uri)).append("), ").append(lit(kotlin, mime)).append(")").append(eol);
            } else if (!uri.isEmpty()) {
                sb.append("intent.setData(Uri.parse(").append(lit(kotlin, uri)).append("))").append(eol);
            } else if (!mime.isEmpty()) {
                sb.append("intent.setType(").append(lit(kotlin, mime)).append(")").append(eol);
            }
        }
        if (m.useCategory) {
            for (String c : m.categories) {
                if (c != null && !c.trim().isEmpty()) sb.append("intent.addCategory(").append(lit(kotlin, c.trim())).append(")").append(eol);
            }
        }
        if (m.useExtras) {
            int[] counter = {0};
            for (IntentModel.ExtraEntry e : m.extras) appendCodeExtra(sb, "intent", e, kotlin, counter, true);
        }
        if (m.useClipData && !m.clipDataItems.isEmpty()) {
            sb.append("// ClipData\n");
            String label = m.clipDataLabel.trim().isEmpty() ? "Intenter ClipData" : m.clipDataLabel.trim();
            List<String> mimes = new ArrayList<>();
            for (String s : m.clipDataMimeTypes) if (s != null && !s.trim().isEmpty()) mimes.add(s.trim());
            if (mimes.isEmpty()) mimes.add("text/plain");
            StringBuilder mimeArr = new StringBuilder();
            for (int i = 0; i < mimes.size(); i++) { if (i > 0) mimeArr.append(", "); mimeArr.append(lit(kotlin, mimes.get(i))); }
            boolean first = true;
            for (IntentModel.ClipDataItem item : m.clipDataItems) {
                if (item.value == null || item.value.isEmpty()) continue;
                String itemExpr;
                switch (item.type == null ? "Text" : item.type) {
                    case "Uri":    itemExpr = "ClipData.Item(Uri.parse(" + lit(kotlin, item.value) + "))"; break;
                    case "Html":   itemExpr = "ClipData.Item(" + lit(kotlin, item.value) + ", " + lit(kotlin, item.value) + ")"; break;
                    case "Intent": itemExpr = "ClipData.Item(Intent.parseUri(" + lit(kotlin, item.value) + ", Intent.URI_INTENT_SCHEME))"; break;
                    default:       itemExpr = "ClipData.Item(" + lit(kotlin, item.value) + ")"; break;
                }
                if (!kotlin) itemExpr = "new " + itemExpr;
                if (first) {
                    if (kotlin) sb.append("val clip = ClipData(").append(lit(kotlin, label)).append(", arrayOf(").append(mimeArr).append("), ").append(itemExpr).append(")\n");
                    else sb.append("ClipData clip = new ClipData(").append(lit(kotlin, label)).append(", new String[]{").append(mimeArr).append("}, ").append(itemExpr).append(");\n");
                    first = false;
                } else {
                    sb.append("clip.addItem(").append(itemExpr).append(")").append(eol);
                }
            }
            if (!first) sb.append("intent.setClipData(clip)").append(eol);
        }
        if (m.useFlags) {
            List<String> names = new ArrayList<>();
            for (String n : m.flagNames) if (FlagRegistry.valueOf(n) != null) names.add("Intent." + n);
            int custom = 0;
            try { custom = FlagRegistry.parseCustom(m.customFlags); } catch (IllegalArgumentException ignored) {}
            if (custom != 0) names.add("0x" + Integer.toHexString(custom));
            if (!names.isEmpty()) {
                sb.append("intent.addFlags(");
                for (int i = 0; i < names.size(); i++) { if (i > 0) sb.append(kotlin ? " or " : " | "); sb.append(names.get(i)); }
                sb.append(")").append(eol);
            }
        }
        if (m.useAdvanced && !m.identifier.trim().isEmpty()) {
            sb.append("intent.setIdentifier(").append(lit(kotlin, m.identifier.trim())).append(")").append(eol);
        }
        String target = "intent";
        if (m.useChooser && IntentModel.isActivityMode(m.launchType)) {
            String title = m.chooserTitle.trim().isEmpty() ? "null" : lit(kotlin, m.chooserTitle.trim());
            if (kotlin) sb.append("val chooser = Intent.createChooser(intent, ").append(title).append(")\n");
            else sb.append("Intent chooser = Intent.createChooser(intent, ").append(title).append(");\n");
            target = "chooser";
        }
        String perm = m.useAdvanced && !m.permission.trim().isEmpty() ? lit(kotlin, m.permission.trim()) : "null";
        switch (m.launchType) {
            case IntentModel.MODE_ACTIVITY_RESULT:
                sb.append("startActivityForResult(").append(target).append(", 1001)").append(eol); break;
            case IntentModel.MODE_SERVICE:
                sb.append("startService(").append(target).append(")").append(eol); break;
            case IntentModel.MODE_FG_SERVICE:
                sb.append("ContextCompat.startForegroundService(context, ").append(target).append(")").append(eol); break;
            case IntentModel.MODE_STOP_SERVICE:
                sb.append("stopService(").append(target).append(")").append(eol); break;
            case IntentModel.MODE_BIND_SERVICE:
                sb.append("bindService(").append(target).append(", connection, Context.BIND_AUTO_CREATE)").append(eol); break;
            case IntentModel.MODE_BROADCAST:
                if ("null".equals(perm)) sb.append("sendBroadcast(").append(target).append(")").append(eol);
                else sb.append("sendBroadcast(").append(target).append(", ").append(perm).append(")").append(eol);
                break;
            case IntentModel.MODE_ORDERED_BROADCAST:
                sb.append("sendOrderedBroadcast(").append(target).append(", ").append(perm).append(")").append(eol); break;
            case IntentModel.MODE_RESOLVE:
                if (kotlin) sb.append("val handlers = packageManager.queryIntentActivities(").append(target).append(", 0)\n");
                else sb.append("List<ResolveInfo> handlers = getPackageManager().queryIntentActivities(").append(target).append(", 0);\n");
                break;
            default:
                sb.append("startActivity(").append(target).append(")").append(eol);
        }
        for (String a : extraActions) {
            sb.append("// also launched with action ").append(a).append('\n');
        }
        return sb.toString().trim();
    }

    private static void appendCodeExtra(StringBuilder sb, String target, IntentModel.ExtraEntry e,
                                        boolean kotlin, int[] counter, boolean isIntent) {
        String key = e.key == null ? "" : e.key;
        if (key.isEmpty()) return;
        String k = lit(kotlin, key);
        String v = e.value == null ? "" : e.value;
        String type = e.type == null ? ExtraTypes.STRING : e.type;
        String eol = kotlin ? "\n" : ";\n";
        String put = isIntent ? "putExtra" : "put";
        String suffix = isIntent ? "" : capitalizeForBundle(type);
        switch (type) {
            case ExtraTypes.BUNDLE: {
                counter[0]++;
                String var = "bundle" + counter[0];
                sb.append(kotlin ? "val " + var + " = Bundle()\n" : "Bundle " + var + " = new Bundle();\n");
                for (IntentModel.ExtraEntry c : e.children) appendCodeExtra(sb, var, c, kotlin, counter, false);
                sb.append(target).append('.').append(isIntent ? "putExtra" : "putBundle").append('(').append(k).append(", ").append(var).append(")").append(eol);
                return;
            }
            case ExtraTypes.NULL:
                sb.append(target).append('.').append(isIntent ? "putExtra" : "putString").append('(').append(k)
                        .append(kotlin ? ", null as String?)" : ", (String) null)").append(eol);
                return;
            case ExtraTypes.STRING:        emit(sb, target, put, suffix, k, lit(kotlin, v), eol); return;
            case ExtraTypes.CHAR_SEQUENCE: emit(sb, target, put, suffix, k, lit(kotlin, v) + (kotlin ? " as CharSequence" : ""), eol); return;
            case ExtraTypes.INT:           emit(sb, target, put, suffix, k, v.trim(), eol); return;
            case ExtraTypes.LONG:          emit(sb, target, put, suffix, k, v.trim() + "L", eol); return;
            case ExtraTypes.SHORT:         emit(sb, target, put, suffix, k, kotlin ? v.trim() + ".toShort()" : "(short) " + v.trim(), eol); return;
            case ExtraTypes.BYTE:          emit(sb, target, put, suffix, k, kotlin ? v.trim() + ".toByte()" : "(byte) " + v.trim(), eol); return;
            case ExtraTypes.CHAR:          emit(sb, target, put, suffix, k, "'" + (v.isEmpty() ? " " : v.substring(0, 1).replace("'", "\\'")) + "'", eol); return;
            case ExtraTypes.BOOLEAN:       emit(sb, target, put, suffix, k, String.valueOf(IntentBuilder.parseBoolean(v)), eol); return;
            case ExtraTypes.FLOAT:         emit(sb, target, put, suffix, k, v.trim() + "f", eol); return;
            case ExtraTypes.DOUBLE:        emit(sb, target, put, suffix, k, v.trim(), eol); return;
            case ExtraTypes.URI:           emit(sb, target, put, isIntent ? "" : "Parcelable", k, "Uri.parse(" + lit(kotlin, v.trim()) + ")", eol); return;
            case ExtraTypes.COMPONENT_NAME: emit(sb, target, put, isIntent ? "" : "Parcelable", k, "ComponentName.unflattenFromString(" + lit(kotlin, v.trim()) + ")", eol); return;
            case ExtraTypes.INTENT:        emit(sb, target, put, isIntent ? "" : "Parcelable", k, "Intent.parseUri(" + lit(kotlin, v.trim()) + ", Intent.URI_INTENT_SCHEME)", eol); return;
            case ExtraTypes.STRING_ARRAY:  emit(sb, target, put, suffix, k, arrayLiteral(kotlin, "String", "arrayOf", quoteAll(kotlin, IntentBuilder.splitStrings(v))), eol); return;
            case ExtraTypes.INT_ARRAY:     emit(sb, target, put, suffix, k, arrayLiteral(kotlin, "int", "intArrayOf", IntentBuilder.splitTrimmed(v)), eol); return;
            case ExtraTypes.LONG_ARRAY:    emit(sb, target, put, suffix, k, arrayLiteral(kotlin, "long", "longArrayOf", suffixAll(IntentBuilder.splitTrimmed(v), "L")), eol); return;
            case ExtraTypes.BOOLEAN_ARRAY: emit(sb, target, put, suffix, k, arrayLiteral(kotlin, "boolean", "booleanArrayOf", IntentBuilder.splitTrimmed(v)), eol); return;
            case ExtraTypes.FLOAT_ARRAY:   emit(sb, target, put, suffix, k, arrayLiteral(kotlin, "float", "floatArrayOf", suffixAll(IntentBuilder.splitTrimmed(v), "f")), eol); return;
            case ExtraTypes.DOUBLE_ARRAY:  emit(sb, target, put, suffix, k, arrayLiteral(kotlin, "double", "doubleArrayOf", IntentBuilder.splitTrimmed(v)), eol); return;
            case ExtraTypes.STRING_LIST:   emit(sb, target, isIntent ? "putStringArrayListExtra" : "putStringArrayList", "", k, listLiteral(kotlin, "String", quoteAll(kotlin, IntentBuilder.splitStrings(v))), eol); return;
            case ExtraTypes.INT_LIST:      emit(sb, target, isIntent ? "putIntegerArrayListExtra" : "putIntegerArrayList", "", k, listLiteral(kotlin, "Integer", IntentBuilder.splitTrimmed(v)), eol); return;
            default:
                sb.append("// TODO extra ").append(k).append(" of type ").append(type).append(" = ").append(lit(kotlin, v)).append('\n');
        }
    }

    private static void emit(StringBuilder sb, String target, String put, String suffix, String k, String value, String eol) {
        sb.append(target).append('.').append(put).append(suffix).append('(').append(k).append(", ").append(value).append(")").append(eol);
    }

    private static String capitalizeForBundle(String type) {
        switch (type) {
            case ExtraTypes.INT: return "Int";
            case ExtraTypes.CHAR_SEQUENCE: return "CharSequence";
            case ExtraTypes.STRING_ARRAY: return "StringArray";
            case ExtraTypes.INT_ARRAY: return "IntArray";
            case ExtraTypes.LONG_ARRAY: return "LongArray";
            case ExtraTypes.BOOLEAN_ARRAY: return "BooleanArray";
            case ExtraTypes.FLOAT_ARRAY: return "FloatArray";
            case ExtraTypes.DOUBLE_ARRAY: return "DoubleArray";
            default: return type;
        }
    }

    private static String[] quoteAll(boolean kotlin, String[] parts) {
        String[] out = new String[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = lit(kotlin, parts[i]);
        return out;
    }

    private static String lit(boolean kotlin, String s) {
        return literal(s, kotlin);
    }

    private static String[] suffixAll(String[] parts, String suffix) {
        String[] out = new String[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = parts[i] + suffix;
        return out;
    }

    private static String arrayLiteral(boolean kotlin, String javaType, String kotlinFn, String[] items) {
        StringBuilder sb = new StringBuilder(kotlin ? kotlinFn + "(" : "new " + javaType + "[]{");
        for (int i = 0; i < items.length; i++) { if (i > 0) sb.append(", "); sb.append(items[i]); }
        return sb.append(kotlin ? ")" : "}").toString();
    }

    private static String listLiteral(boolean kotlin, String javaType, String[] items) {
        StringBuilder sb = new StringBuilder(kotlin ? "arrayListOf(" : "new ArrayList<>(Arrays.asList(");
        if (!kotlin && items.length == 0) return "new ArrayList<" + javaType + ">()";
        for (int i = 0; i < items.length; i++) { if (i > 0) sb.append(", "); sb.append(items[i]); }
        return sb.append(kotlin ? ")" : "))").toString();
    }

    public static String javaString(String s) {
        return literal(s, false);
    }

    public static String kotlinString(String s) {
        return literal(s, true);
    }

    private static String literal(String s, boolean kotlin) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '$': sb.append(kotlin ? "\\$" : "$"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Import
    // ══════════════════════════════════════════════════════════════════════

    /** Detects the format of pasted text and converts it into a model. */
    public static IntentModel importAny(String text) throws Exception {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) throw new IllegalArgumentException("Nothing to import");
        if (t.startsWith("{")) return IntentModel.fromJsonString(t);
        if (t.startsWith("intent:") || t.startsWith("android-app:") || t.startsWith("#Intent;")) {
            if (t.startsWith("#Intent;")) t = "intent:" + t;
            return fromUri(t);
        }
        String lower = t.toLowerCase();
        if (lower.contains("am start") || lower.contains("am broadcast") || lower.contains("am startservice")
                || lower.contains("am start-foreground-service") || lower.contains("am stopservice")
                || lower.startsWith("am ") || lower.startsWith("adb ")) {
            return fromAm(t);
        }
        // Plain URI → VIEW intent
        return fromUri(t);
    }

    public static IntentModel fromUri(String uri) throws URISyntaxException {
        Intent intent = Intent.parseUri(uri,
                Intent.URI_INTENT_SCHEME | Intent.URI_ANDROID_APP_SCHEME | Intent.URI_ALLOW_UNSAFE);
        return fromIntent(intent);
    }

    /** Builds a model that reproduces the given intent as closely as possible. */
    public static IntentModel fromIntent(Intent intent) {
        IntentModel m = new IntentModel();
        if (intent == null) return m;
        if (intent.getAction() != null) {
            m.useAction = true;
            m.actions.add(intent.getAction());
            m.action = intent.getAction();
        }
        if (intent.getComponent() != null) {
            m.useComponent = true;
            m.packageName = intent.getComponent().getPackageName();
            m.componentName = intent.getComponent().getClassName();
        } else if (intent.getPackage() != null) {
            m.useComponent = true;
            m.packageName = intent.getPackage();
        }
        if (intent.getDataString() != null || intent.getType() != null) {
            m.useData = true;
            m.dataUri = intent.getDataString() == null ? "" : intent.getDataString();
            m.mimeType = intent.getType() == null ? "" : intent.getType();
        }
        if (intent.getCategories() != null && !intent.getCategories().isEmpty()) {
            m.useCategory = true;
            m.categories.addAll(intent.getCategories());
        }
        if (intent.getFlags() != 0) {
            m.useFlags = true;
            m.flagNames.addAll(FlagRegistry.namesFor(intent.getFlags()));
            int unknown = FlagRegistry.unknownBits(intent.getFlags());
            if (unknown != 0) m.customFlags = "0x" + Integer.toHexString(unknown);
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && intent.getIdentifier() != null) {
            m.useAdvanced = true;
            m.identifier = intent.getIdentifier();
        }
        ClipData clip = intent.getClipData();
        if (clip != null && clip.getItemCount() > 0) {
            m.useClipData = true;
            if (clip.getDescription() != null) {
                CharSequence label = clip.getDescription().getLabel();
                m.clipDataLabel = label == null ? "" : label.toString();
                for (int i = 0; i < clip.getDescription().getMimeTypeCount(); i++) {
                    m.clipDataMimeTypes.add(clip.getDescription().getMimeType(i));
                }
            }
            for (int i = 0; i < clip.getItemCount(); i++) {
                ClipData.Item item = clip.getItemAt(i);
                if (item.getUri() != null) m.clipDataItems.add(new IntentModel.ClipDataItem("Uri", item.getUri().toString()));
                else if (item.getIntent() != null) m.clipDataItems.add(new IntentModel.ClipDataItem("Intent", item.getIntent().toUri(Intent.URI_INTENT_SCHEME)));
                else if (item.getHtmlText() != null) m.clipDataItems.add(new IntentModel.ClipDataItem("Html", item.getHtmlText()));
                else if (item.getText() != null) m.clipDataItems.add(new IntentModel.ClipDataItem("Text", item.getText().toString()));
            }
        }
        Bundle extras = null;
        try {
            extras = intent.getExtras();
        } catch (RuntimeException ignored) {}
        if (extras != null && !extras.isEmpty()) {
            m.useExtras = true;
            m.extras.addAll(entriesFromBundle(extras));
        }
        m.label = m.generateLabel();
        return m;
    }

    /** Converts a bundle to typed entries; unsupported types become String with a note. */
    public static List<IntentModel.ExtraEntry> entriesFromBundle(Bundle b) {
        List<IntentModel.ExtraEntry> out = new ArrayList<>();
        List<String> keys;
        try {
            keys = new ArrayList<>(b.keySet());
        } catch (RuntimeException e) {
            return out;
        }
        java.util.Collections.sort(keys);
        for (String key : keys) {
            Object v;
            try { v = b.get(key); } catch (RuntimeException e) { continue; }
            out.add(entryFromValue(key, v));
        }
        return out;
    }

    public static IntentModel.ExtraEntry entryFromValue(String key, Object v) {
        if (v == null) return new IntentModel.ExtraEntry(key, "", ExtraTypes.NULL);
        if (v instanceof String) return new IntentModel.ExtraEntry(key, (String) v, ExtraTypes.STRING);
        if (v instanceof Integer) return new IntentModel.ExtraEntry(key, v.toString(), ExtraTypes.INT);
        if (v instanceof Long) return new IntentModel.ExtraEntry(key, v.toString(), ExtraTypes.LONG);
        if (v instanceof Short) return new IntentModel.ExtraEntry(key, v.toString(), ExtraTypes.SHORT);
        if (v instanceof Byte) return new IntentModel.ExtraEntry(key, v.toString(), ExtraTypes.BYTE);
        if (v instanceof Character) return new IntentModel.ExtraEntry(key, v.toString(), ExtraTypes.CHAR);
        if (v instanceof Boolean) return new IntentModel.ExtraEntry(key, v.toString(), ExtraTypes.BOOLEAN);
        if (v instanceof Float) return new IntentModel.ExtraEntry(key, v.toString(), ExtraTypes.FLOAT);
        if (v instanceof Double) return new IntentModel.ExtraEntry(key, v.toString(), ExtraTypes.DOUBLE);
        if (v instanceof CharSequence) return new IntentModel.ExtraEntry(key, v.toString(), ExtraTypes.CHAR_SEQUENCE);
        if (v instanceof Uri) return new IntentModel.ExtraEntry(key, v.toString(), ExtraTypes.URI);
        if (v instanceof ComponentName) return new IntentModel.ExtraEntry(key, ((ComponentName) v).flattenToString(), ExtraTypes.COMPONENT_NAME);
        if (v instanceof Intent) return new IntentModel.ExtraEntry(key, ((Intent) v).toUri(Intent.URI_INTENT_SCHEME), ExtraTypes.INTENT);
        if (v instanceof Bundle) return IntentModel.ExtraEntry.bundle(key, entriesFromBundle((Bundle) v));
        if (v instanceof int[]) return new IntentModel.ExtraEntry(key, strip(java.util.Arrays.toString((int[]) v)), ExtraTypes.INT_ARRAY);
        if (v instanceof long[]) return new IntentModel.ExtraEntry(key, strip(java.util.Arrays.toString((long[]) v)), ExtraTypes.LONG_ARRAY);
        if (v instanceof short[]) return new IntentModel.ExtraEntry(key, strip(java.util.Arrays.toString((short[]) v)), ExtraTypes.SHORT_ARRAY);
        if (v instanceof boolean[]) return new IntentModel.ExtraEntry(key, strip(java.util.Arrays.toString((boolean[]) v)), ExtraTypes.BOOLEAN_ARRAY);
        if (v instanceof float[]) return new IntentModel.ExtraEntry(key, strip(java.util.Arrays.toString((float[]) v)), ExtraTypes.FLOAT_ARRAY);
        if (v instanceof double[]) return new IntentModel.ExtraEntry(key, strip(java.util.Arrays.toString((double[]) v)), ExtraTypes.DOUBLE_ARRAY);
        if (v instanceof char[]) return new IntentModel.ExtraEntry(key, new String((char[]) v), ExtraTypes.CHAR_ARRAY);
        if (v instanceof byte[]) {
            StringBuilder sb = new StringBuilder("0x");
            for (byte x : (byte[]) v) sb.append(String.format("%02X", x));
            return new IntentModel.ExtraEntry(key, sb.toString(), ExtraTypes.BYTE_ARRAY);
        }
        if (v instanceof String[]) return new IntentModel.ExtraEntry(key, joinPlain((Object[]) v), ExtraTypes.STRING_ARRAY);
        if (v instanceof CharSequence[]) return new IntentModel.ExtraEntry(key, joinPlain((Object[]) v), ExtraTypes.CHAR_SEQUENCE_ARRAY);
        if (v instanceof Parcelable[]) {
            Parcelable[] arr = (Parcelable[]) v;
            boolean allUri = arr.length > 0;
            for (Parcelable p : arr) if (!(p instanceof Uri)) { allUri = false; break; }
            if (allUri) return new IntentModel.ExtraEntry(key, joinPlain(arr), ExtraTypes.URI_ARRAY);
        }
        if (v instanceof ArrayList) {
            ArrayList<?> l = (ArrayList<?>) v;
            Object first = l.isEmpty() ? null : l.get(0);
            if (first instanceof Integer) return new IntentModel.ExtraEntry(key, joinPlain(l.toArray()), ExtraTypes.INT_LIST);
            if (first instanceof Uri) return new IntentModel.ExtraEntry(key, joinPlain(l.toArray()), ExtraTypes.URI_LIST);
            if (first instanceof String || first == null) return new IntentModel.ExtraEntry(key, joinPlain(l.toArray()), ExtraTypes.STRING_LIST);
            if (first instanceof CharSequence) return new IntentModel.ExtraEntry(key, joinPlain(l.toArray()), ExtraTypes.CHAR_SEQUENCE_LIST);
        }
        // Fallback: keep the textual form so nothing is silently lost.
        return new IntentModel.ExtraEntry(key, String.valueOf(v), ExtraTypes.STRING);
    }

    private static String strip(String arrayToString) {
        String s = arrayToString.trim();
        if (s.startsWith("[")) s = s.substring(1);
        if (s.endsWith("]")) s = s.substring(0, s.length() - 1);
        return s.replace(", ", ",");
    }

    private static String joinPlain(Object[] arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(arr[i] == null ? "" : arr[i].toString());
        }
        return sb.toString();
    }

    // ─── am command parsing ──────────────────────────────────────────────

    /** Parses an {@code [adb shell] am <verb> …} command into a model. */
    public static IntentModel fromAm(String command) {
        List<String> tokens = tokenize(command);
        IntentModel m = new IntentModel();
        int i = 0;
        // Skip "adb", "shell", "-s serial", "am"
        while (i < tokens.size()) {
            String t = tokens.get(i);
            if (t.equals("adb") || t.equals("shell") || t.equals("am") || t.equals("cmd") || t.equals("activity")) { i++; continue; }
            if (t.equals("-s") && i + 1 < tokens.size() && !tokens.get(i + 1).startsWith("-")) { i += 2; continue; }
            break;
        }
        if (i < tokens.size()) {
            String verb = tokens.get(i);
            switch (verb) {
                case "start": case "start-activity": m.launchType = IntentModel.MODE_ACTIVITY; i++; break;
                case "startservice": case "start-service": m.launchType = IntentModel.MODE_SERVICE; i++; break;
                case "start-foreground-service": case "startforegroundservice": m.launchType = IntentModel.MODE_FG_SERVICE; i++; break;
                case "stopservice": case "stop-service": m.launchType = IntentModel.MODE_STOP_SERVICE; i++; break;
                case "broadcast": m.launchType = IntentModel.MODE_BROADCAST; i++; break;
                default: break;
            }
        }
        String positional = null;
        while (i < tokens.size()) {
            String t = tokens.get(i);
            String next = i + 1 < tokens.size() ? tokens.get(i + 1) : null;
            String next2 = i + 2 < tokens.size() ? tokens.get(i + 2) : null;
            switch (t) {
                case "-a": case "--action": if (next != null) { m.useAction = true; m.actions.add(next); m.action = next; } i += 2; break;
                case "-d": case "--data": if (next != null) { m.useData = true; m.dataUri = next; } i += 2; break;
                case "-t": case "--type": if (next != null) { m.useData = true; m.mimeType = next; } i += 2; break;
                case "-c": case "--category": if (next != null) { m.useCategory = true; m.categories.add(next); } i += 2; break;
                case "-n": case "--component": if (next != null) { ComponentName cn = ComponentName.unflattenFromString(next); m.useComponent = true; if (cn != null) { m.packageName = cn.getPackageName(); m.componentName = cn.getClassName(); } else { m.componentName = next; } } i += 2; break;
                case "-p": case "--package": if (next != null) { m.useComponent = true; m.packageName = next; } i += 2; break;
                case "-i": case "--identifier": if (next != null) { m.useAdvanced = true; m.identifier = next; } i += 2; break;
                case "-f": case "--flags": if (next != null) { m.useFlags = true; try { int f = FlagRegistry.parseCustom(next); m.flagNames.addAll(FlagRegistry.namesFor(f)); int unk = FlagRegistry.unknownBits(f); if (unk != 0) m.customFlags = "0x" + Integer.toHexString(unk); } catch (IllegalArgumentException e) { m.customFlags = next; } } i += 2; break;
                case "--receiver-permission": if (next != null) { m.useAdvanced = true; m.permission = next; } i += 2; break;
                case "--esn": if (next != null) { addExtra(m, next, "", ExtraTypes.NULL); } i += 2; break;
                case "-e": case "--es": if (next != null) { addExtra(m, next, next2 == null ? "" : next2, ExtraTypes.STRING); } i += 3; break;
                case "--ez": if (next != null) { addExtra(m, next, next2 == null ? "" : next2, ExtraTypes.BOOLEAN); } i += 3; break;
                case "--ei": if (next != null) { addExtra(m, next, next2 == null ? "" : next2, ExtraTypes.INT); } i += 3; break;
                case "--el": if (next != null) { addExtra(m, next, next2 == null ? "" : next2, ExtraTypes.LONG); } i += 3; break;
                case "--ef": if (next != null) { addExtra(m, next, next2 == null ? "" : next2, ExtraTypes.FLOAT); } i += 3; break;
                case "--ed": if (next != null) { addExtra(m, next, next2 == null ? "" : next2, ExtraTypes.DOUBLE); } i += 3; break;
                case "--eu": if (next != null) { addExtra(m, next, next2 == null ? "" : next2, ExtraTypes.URI); } i += 3; break;
                case "--ecn": if (next != null) { addExtra(m, next, next2 == null ? "" : next2, ExtraTypes.COMPONENT_NAME); } i += 3; break;
                case "--eia": if (next != null) { addExtra(m, next, next2 == null ? "" : next2, ExtraTypes.INT_ARRAY); } i += 3; break;
                case "--eial": if (next != null) { addExtra(m, next, next2 == null ? "" : next2, ExtraTypes.INT_LIST); } i += 3; break;
                case "--ela": if (next != null) { addExtra(m, next, next2 == null ? "" : next2, ExtraTypes.LONG_ARRAY); } i += 3; break;
                case "--elal": if (next != null) { addExtra(m, next, next2 == null ? "" : next2, ExtraTypes.LONG_ARRAY); } i += 3; break;
                case "--efa": if (next != null) { addExtra(m, next, next2 == null ? "" : next2, ExtraTypes.FLOAT_ARRAY); } i += 3; break;
                case "--efal": if (next != null) { addExtra(m, next, next2 == null ? "" : next2, ExtraTypes.FLOAT_ARRAY); } i += 3; break;
                case "--eda": case "--edal": if (next != null) { addExtra(m, next, next2 == null ? "" : next2, ExtraTypes.DOUBLE_ARRAY); } i += 3; break;
                case "--esa": if (next != null) { addExtra(m, next, next2 == null ? "" : next2.replace("\\,", ","), ExtraTypes.STRING_ARRAY); } i += 3; break;
                case "--esal": if (next != null) { addExtra(m, next, next2 == null ? "" : next2.replace("\\,", ","), ExtraTypes.STRING_LIST); } i += 3; break;
                case "--grant-read-uri-permission": addFlag(m, "FLAG_GRANT_READ_URI_PERMISSION"); i++; break;
                case "--grant-write-uri-permission": addFlag(m, "FLAG_GRANT_WRITE_URI_PERMISSION"); i++; break;
                case "--grant-persistable-uri-permission": addFlag(m, "FLAG_GRANT_PERSISTABLE_URI_PERMISSION"); i++; break;
                case "--grant-prefix-uri-permission": addFlag(m, "FLAG_GRANT_PREFIX_URI_PERMISSION"); i++; break;
                case "--debug-log-resolution": addFlag(m, "FLAG_DEBUG_LOG_RESOLUTION"); i++; break;
                case "--exclude-stopped-packages": addFlag(m, "FLAG_EXCLUDE_STOPPED_PACKAGES"); i++; break;
                case "--include-stopped-packages": addFlag(m, "FLAG_INCLUDE_STOPPED_PACKAGES"); i++; break;
                case "--activity-brought-to-front": addFlag(m, "FLAG_ACTIVITY_BROUGHT_TO_FRONT"); i++; break;
                case "--activity-clear-top": addFlag(m, "FLAG_ACTIVITY_CLEAR_TOP"); i++; break;
                case "--activity-clear-when-task-reset": addFlag(m, "FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET"); i++; break;
                case "--activity-exclude-from-recents": addFlag(m, "FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS"); i++; break;
                case "--activity-launched-from-history": i++; break;
                case "--activity-multiple-task": addFlag(m, "FLAG_ACTIVITY_MULTIPLE_TASK"); i++; break;
                case "--activity-no-animation": addFlag(m, "FLAG_ACTIVITY_NO_ANIMATION"); i++; break;
                case "--activity-no-history": addFlag(m, "FLAG_ACTIVITY_NO_HISTORY"); i++; break;
                case "--activity-no-user-action": addFlag(m, "FLAG_ACTIVITY_NO_USER_ACTION"); i++; break;
                case "--activity-previous-is-top": addFlag(m, "FLAG_ACTIVITY_PREVIOUS_IS_TOP"); i++; break;
                case "--activity-reorder-to-front": addFlag(m, "FLAG_ACTIVITY_REORDER_TO_FRONT"); i++; break;
                case "--activity-reset-task-if-needed": addFlag(m, "FLAG_ACTIVITY_RESET_TASK_IF_NEEDED"); i++; break;
                case "--activity-single-top": addFlag(m, "FLAG_ACTIVITY_SINGLE_TOP"); i++; break;
                case "--activity-clear-task": addFlag(m, "FLAG_ACTIVITY_CLEAR_TASK"); i++; break;
                case "--activity-task-on-home": addFlag(m, "FLAG_ACTIVITY_TASK_ON_HOME"); i++; break;
                case "--activity-match-external": addFlag(m, "FLAG_ACTIVITY_MATCH_EXTERNAL"); i++; break;
                case "--receiver-registered-only": addFlag(m, "FLAG_RECEIVER_REGISTERED_ONLY"); i++; break;
                case "--receiver-replace-pending": addFlag(m, "FLAG_RECEIVER_REPLACE_PENDING"); i++; break;
                case "--receiver-foreground": addFlag(m, "FLAG_RECEIVER_FOREGROUND"); i++; break;
                case "--receiver-no-abort": addFlag(m, "FLAG_RECEIVER_NO_ABORT"); i++; break;
                case "--receiver-include-background": i++; break;
                case "-W": case "--wait": case "-D": case "-S": case "-R": case "--stop": case "--selector": i++; break;
                case "--user": case "--display": case "--windowingMode": case "--activityType": case "--task": case "-P": case "--start-profiler": case "--sampling": i += 2; break;
                default:
                    if (!t.startsWith("-")) positional = t;
                    i++;
            }
        }
        if (positional != null) {
            if (positional.contains("/") && !positional.contains("://") && !positional.contains(":")) {
                ComponentName cn = ComponentName.unflattenFromString(positional);
                if (cn != null) { m.useComponent = true; m.packageName = cn.getPackageName(); m.componentName = cn.getClassName(); }
            } else if (positional.contains(":")) {
                m.useData = true;
                if (m.dataUri.isEmpty()) m.dataUri = positional;
            } else {
                m.useComponent = true;
                if (m.packageName.isEmpty()) m.packageName = positional;
            }
        }
        m.label = m.generateLabel();
        return m;
    }

    private static void addExtra(IntentModel m, String key, String value, String type) {
        m.useExtras = true;
        m.extras.add(new IntentModel.ExtraEntry(key, value, type));
    }

    private static void addFlag(IntentModel m, String flag) {
        m.useFlags = true;
        if (!m.flagNames.contains(flag)) m.flagNames.add(flag);
    }

    /** Shell-style tokenizer supporting single quotes, double quotes and backslash escapes. */
    public static List<String> tokenize(String s) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inSingle = false, inDouble = false, hasToken = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inSingle) {
                if (c == '\'') inSingle = false; else cur.append(c);
                continue;
            }
            if (inDouble) {
                if (c == '"') inDouble = false;
                else if (c == '\\' && i + 1 < s.length()) { cur.append(s.charAt(++i)); }
                else cur.append(c);
                continue;
            }
            if (c == '\'') { inSingle = true; hasToken = true; continue; }
            if (c == '"') { inDouble = true; hasToken = true; continue; }
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(i + 1);
                if (n == '\n') { i++; continue; }
                cur.append(n); i++; hasToken = true; continue;
            }
            if (Character.isWhitespace(c)) {
                if (hasToken) { out.add(cur.toString()); cur.setLength(0); hasToken = false; }
                continue;
            }
            cur.append(c);
            hasToken = true;
        }
        if (hasToken) out.add(cur.toString());
        return out;
    }
}
