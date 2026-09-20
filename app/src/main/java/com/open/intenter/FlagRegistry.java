package com.open.intenter;

import android.content.Intent;
import android.os.Build;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * All public Intent flags, grouped for display, with helpers to parse custom
 * flag expressions and to describe a flag bitmask.
 */
public final class FlagRegistry {

    private FlagRegistry() {}

    public static final class Group {
        public final String title;
        public final LinkedHashMap<String, Integer> flags = new LinkedHashMap<>();
        Group(String title) { this.title = title; }
    }

    private static final List<Group> GROUPS = new ArrayList<>();
    private static final LinkedHashMap<String, Integer> ALL = new LinkedHashMap<>();

    static {
        Group activity = new Group("Activity");
        put(activity, "FLAG_ACTIVITY_NEW_TASK", Intent.FLAG_ACTIVITY_NEW_TASK);
        put(activity, "FLAG_ACTIVITY_CLEAR_TOP", Intent.FLAG_ACTIVITY_CLEAR_TOP);
        put(activity, "FLAG_ACTIVITY_SINGLE_TOP", Intent.FLAG_ACTIVITY_SINGLE_TOP);
        put(activity, "FLAG_ACTIVITY_CLEAR_TASK", Intent.FLAG_ACTIVITY_CLEAR_TASK);
        put(activity, "FLAG_ACTIVITY_NO_HISTORY", Intent.FLAG_ACTIVITY_NO_HISTORY);
        put(activity, "FLAG_ACTIVITY_MULTIPLE_TASK", Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        put(activity, "FLAG_ACTIVITY_NEW_DOCUMENT", Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        put(activity, "FLAG_ACTIVITY_REORDER_TO_FRONT", Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        put(activity, "FLAG_ACTIVITY_FORWARD_RESULT", Intent.FLAG_ACTIVITY_FORWARD_RESULT);
        put(activity, "FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS", Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        put(activity, "FLAG_ACTIVITY_RETAIN_IN_RECENTS", Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS);
        put(activity, "FLAG_ACTIVITY_BROUGHT_TO_FRONT", Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
        put(activity, "FLAG_ACTIVITY_PREVIOUS_IS_TOP", Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);
        put(activity, "FLAG_ACTIVITY_TASK_ON_HOME", Intent.FLAG_ACTIVITY_TASK_ON_HOME);
        put(activity, "FLAG_ACTIVITY_LAUNCH_ADJACENT", Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);
        put(activity, "FLAG_ACTIVITY_NO_ANIMATION", Intent.FLAG_ACTIVITY_NO_ANIMATION);
        put(activity, "FLAG_ACTIVITY_NO_USER_ACTION", Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        put(activity, "FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET", Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        put(activity, "FLAG_ACTIVITY_RESET_TASK_IF_NEEDED", Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        put(activity, "FLAG_ACTIVITY_MATCH_EXTERNAL", Intent.FLAG_ACTIVITY_MATCH_EXTERNAL);
        put(activity, "FLAG_ACTIVITY_REQUIRE_NON_BROWSER", Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER);
        put(activity, "FLAG_ACTIVITY_REQUIRE_DEFAULT", Intent.FLAG_ACTIVITY_REQUIRE_DEFAULT);
        GROUPS.add(activity);

        Group grant = new Group("URI permission grants");
        put(grant, "FLAG_GRANT_READ_URI_PERMISSION", Intent.FLAG_GRANT_READ_URI_PERMISSION);
        put(grant, "FLAG_GRANT_WRITE_URI_PERMISSION", Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        put(grant, "FLAG_GRANT_PERSISTABLE_URI_PERMISSION", Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        put(grant, "FLAG_GRANT_PREFIX_URI_PERMISSION", Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        GROUPS.add(grant);

        Group receiver = new Group("Broadcast receivers");
        put(receiver, "FLAG_RECEIVER_FOREGROUND", Intent.FLAG_RECEIVER_FOREGROUND);
        put(receiver, "FLAG_RECEIVER_NO_ABORT", Intent.FLAG_RECEIVER_NO_ABORT);
        put(receiver, "FLAG_RECEIVER_REGISTERED_ONLY", Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        put(receiver, "FLAG_RECEIVER_REPLACE_PENDING", Intent.FLAG_RECEIVER_REPLACE_PENDING);
        put(receiver, "FLAG_RECEIVER_VISIBLE_TO_INSTANT_APPS", Intent.FLAG_RECEIVER_VISIBLE_TO_INSTANT_APPS);
        put(receiver, "FLAG_INCLUDE_STOPPED_PACKAGES", Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        put(receiver, "FLAG_EXCLUDE_STOPPED_PACKAGES", Intent.FLAG_EXCLUDE_STOPPED_PACKAGES);
        GROUPS.add(receiver);

        Group other = new Group("Other");
        put(other, "FLAG_DEBUG_LOG_RESOLUTION", Intent.FLAG_DEBUG_LOG_RESOLUTION);
        put(other, "FLAG_FROM_BACKGROUND", Intent.FLAG_FROM_BACKGROUND);
        put(other, "FLAG_DIRECT_BOOT_AUTO", Intent.FLAG_DIRECT_BOOT_AUTO);
        GROUPS.add(other);
    }

    private static void put(Group g, String name, int value) {
        g.flags.put(name, value);
        ALL.put(name, value);
    }

    public static List<Group> groups() { return GROUPS; }

    public static Map<String, Integer> all() { return ALL; }

    public static Integer valueOf(String name) { return ALL.get(name); }

    /** Short label for chips: strips the FLAG_ prefix. */
    public static String shortName(String name) {
        return name.startsWith("FLAG_") ? name.substring(5) : name;
    }

    /**
     * Parses a custom flag expression: one or more hex / decimal values
     * separated by commas, pipes or whitespace. Returns the OR of all values.
     */
    public static int parseCustom(String expr) {
        if (expr == null) return 0;
        String trimmed = expr.trim();
        if (trimmed.isEmpty()) return 0;
        int result = 0;
        for (String part : trimmed.split("[,|\\s]+")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            Integer named = ALL.get(p);
            if (named != null) { result |= named; continue; }
            if (p.startsWith("FLAG_")) named = ALL.get(p);
            if (named != null) { result |= named; continue; }
            try {
                long v;
                if (p.startsWith("0x") || p.startsWith("0X")) {
                    v = Long.parseLong(p.substring(2), 16);
                } else {
                    v = Long.parseLong(p);
                }
                result |= (int) v;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid flag value: " + p);
            }
        }
        return result;
    }

    /** Combined value of the named flags plus a custom expression. */
    public static int combine(List<String> names, String custom) {
        int flags = 0;
        if (names != null) {
            for (String n : names) {
                Integer v = ALL.get(n);
                if (v != null) flags |= v;
            }
        }
        flags |= parseCustom(custom);
        return flags;
    }

    /** Names of all known flags that are set in the mask. */
    public static List<String> namesFor(int flags) {
        List<String> names = new ArrayList<>();
        for (Map.Entry<String, Integer> e : ALL.entrySet()) {
            if ((flags & e.getValue()) == e.getValue() && e.getValue() != 0) names.add(e.getKey());
        }
        return names;
    }

    /** Bits in the mask that don't correspond to a known flag. */
    public static int unknownBits(int flags) {
        int known = 0;
        for (int v : ALL.values()) known |= v;
        return flags & ~known;
    }

    /** Human readable description, e.g. "0x10000000 (NEW_TASK)". */
    public static String describe(int flags) {
        if (flags == 0) return "0";
        StringBuilder sb = new StringBuilder("0x").append(Integer.toHexString(flags));
        List<String> names = namesFor(flags);
        if (!names.isEmpty()) {
            sb.append(" (");
            for (int i = 0; i < names.size(); i++) {
                if (i > 0) sb.append(" | ");
                sb.append(shortName(names.get(i)));
            }
            int unknown = unknownBits(flags);
            if (unknown != 0) sb.append(" | 0x").append(Integer.toHexString(unknown));
            sb.append(')');
        }
        return sb.toString();
    }

    /** Whether a flag exists at this API level (all listed flags are API 24+). */
    public static boolean isSupported(String name) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && ALL.containsKey(name);
    }
}
