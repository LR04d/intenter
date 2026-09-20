package com.open.intenter;

import java.util.Arrays;
import java.util.List;

/**
 * Canonical list of extra value types supported by the builder, plus the
 * helper text shown to the user for each. The strings are also the values
 * persisted in history / favorites, so they must stay stable.
 */
public final class ExtraTypes {

    private ExtraTypes() {}

    public static final String STRING = "String";
    public static final String CHAR_SEQUENCE = "CharSequence";
    public static final String INT = "Integer";
    public static final String LONG = "Long";
    public static final String SHORT = "Short";
    public static final String BYTE = "Byte";
    public static final String CHAR = "Char";
    public static final String BOOLEAN = "Boolean";
    public static final String FLOAT = "Float";
    public static final String DOUBLE = "Double";
    public static final String URI = "Uri";
    public static final String COMPONENT_NAME = "ComponentName";
    public static final String INTENT = "Intent";
    public static final String BUNDLE = "Bundle";
    public static final String NULL = "Null";
    public static final String STRING_ARRAY = "String[]";
    public static final String CHAR_SEQUENCE_ARRAY = "CharSequence[]";
    public static final String INT_ARRAY = "int[]";
    public static final String LONG_ARRAY = "long[]";
    public static final String SHORT_ARRAY = "short[]";
    public static final String BYTE_ARRAY = "byte[]";
    public static final String CHAR_ARRAY = "char[]";
    public static final String BOOLEAN_ARRAY = "boolean[]";
    public static final String FLOAT_ARRAY = "float[]";
    public static final String DOUBLE_ARRAY = "double[]";
    public static final String URI_ARRAY = "Uri[]";
    public static final String STRING_LIST = "ArrayList<String>";
    public static final String INT_LIST = "ArrayList<Integer>";
    public static final String CHAR_SEQUENCE_LIST = "ArrayList<CharSequence>";
    public static final String URI_LIST = "ArrayList<Uri>";

    /** Every type, in the order shown in the type picker. */
    public static final String[] ALL = {
            STRING, INT, LONG, BOOLEAN, FLOAT, DOUBLE, SHORT, BYTE, CHAR, CHAR_SEQUENCE,
            URI, COMPONENT_NAME, INTENT, BUNDLE, NULL,
            STRING_ARRAY, INT_ARRAY, LONG_ARRAY, BOOLEAN_ARRAY, FLOAT_ARRAY, DOUBLE_ARRAY,
            SHORT_ARRAY, BYTE_ARRAY, CHAR_ARRAY, CHAR_SEQUENCE_ARRAY, URI_ARRAY,
            STRING_LIST, INT_LIST, CHAR_SEQUENCE_LIST, URI_LIST
    };

    /** Subset that makes sense for ContentValues (provider insert / update). */
    public static final String[] CONTENT_VALUE_TYPES = {
            STRING, INT, LONG, BOOLEAN, FLOAT, DOUBLE, SHORT, BYTE, BYTE_ARRAY, NULL
    };

    public static final List<String> ALL_LIST = Arrays.asList(ALL);

    public static boolean isKnown(String type) {
        return ALL_LIST.contains(type);
    }

    /** True when the type carries nested entries instead of a text value. */
    public static boolean isContainer(String type) {
        return BUNDLE.equals(type);
    }

    /** True when the row should show a value field. */
    public static boolean hasValue(String type) {
        return !BUNDLE.equals(type) && !NULL.equals(type);
    }

    /** Helper text explaining the expected value format. */
    public static String hintFor(String type) {
        if (type == null) return "";
        switch (type) {
            case STRING:
            case CHAR_SEQUENCE:
                return "Any text, newlines allowed";
            case INT:
            case SHORT:
            case BYTE:
                return "Decimal or 0x hex, e.g. 42 or 0x2A";
            case LONG:
                return "Decimal or 0x hex, e.g. 9999999999";
            case CHAR:
                return "Single character or \\u0041";
            case BOOLEAN:
                return "true / false (also 1 / 0, yes / no)";
            case FLOAT:
            case DOUBLE:
                return "e.g. 3.14, -1e9, NaN, Infinity";
            case URI:
                return "e.g. content://authority/path or https://…";
            case COMPONENT_NAME:
                return "pkg/cls e.g. com.app/.MainActivity";
            case INTENT:
                return "intent://…#Intent;…;end or any URI";
            case BUNDLE:
                return "Nested bundle: add child extras below";
            case NULL:
                return "Puts a null value for this key";
            case STRING_ARRAY:
            case CHAR_SEQUENCE_ARRAY:
            case STRING_LIST:
            case CHAR_SEQUENCE_LIST:
                return "Comma-separated, e.g. a,b,c (empty = empty list)";
            case INT_ARRAY:
            case LONG_ARRAY:
            case SHORT_ARRAY:
            case INT_LIST:
                return "Comma-separated numbers, e.g. 1,2,3";
            case BYTE_ARRAY:
                return "0xDEADBEEF, 1,2,3 or utf8:text";
            case CHAR_ARRAY:
                return "Characters of the text";
            case BOOLEAN_ARRAY:
                return "Comma-separated, e.g. true,false";
            case FLOAT_ARRAY:
            case DOUBLE_ARRAY:
                return "Comma-separated, e.g. 1.5,2.5";
            case URI_ARRAY:
            case URI_LIST:
                return "Comma-separated URIs";
            default:
                return "";
        }
    }
}
