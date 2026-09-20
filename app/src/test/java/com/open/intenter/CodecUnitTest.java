package com.open.intenter;

import android.content.Intent;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Pure-JVM tests for parsing helpers that don't need the Android runtime. */
public class CodecUnitTest {

    // ─── Shell tokenizer ─────────────────────────────────────────────────

    @Test
    public void tokenize_handlesQuotesAndEscapes() {
        List<String> t = IntentCodec.tokenize("am start -a 'android.intent.action.VIEW' -d \"https://x.com/a b\" --es k it\\'s");
        assertEquals("am", t.get(0));
        assertEquals("start", t.get(1));
        assertEquals("-a", t.get(2));
        assertEquals("android.intent.action.VIEW", t.get(3));
        assertEquals("-d", t.get(4));
        assertEquals("https://x.com/a b", t.get(5));
        assertEquals("--es", t.get(6));
        assertEquals("k", t.get(7));
        assertEquals("it's", t.get(8));
    }

    @Test
    public void tokenize_lineContinuation() {
        List<String> t = IntentCodec.tokenize("am broadcast \\\n  -a X");
        assertEquals(4, t.size());
        assertEquals("X", t.get(3));
    }

    @Test
    public void shellQuote_onlyWhenNeeded() {
        assertEquals("plain.value", IntentCodec.shellQuote("plain.value"));
        assertEquals("'has space'", IntentCodec.shellQuote("has space"));
        assertEquals("'it'\\''s'", IntentCodec.shellQuote("it's"));
        assertEquals("''", IntentCodec.shellQuote(""));
    }

    @Test
    public void javaString_escapes() {
        assertEquals("\"a\\\"b\\n$c\"", IntentCodec.javaString("a\"b\n$c"));
        assertEquals("\"a\\$c\"", IntentCodec.kotlinString("a$c"));
    }

    // ─── Flags ───────────────────────────────────────────────────────────

    @Test
    public void flags_parseCustomHexDecimalAndNames() {
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, FlagRegistry.parseCustom("0x10000000"));
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, FlagRegistry.parseCustom("268435456"));
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP,
                FlagRegistry.parseCustom("0x10000000 | FLAG_ACTIVITY_CLEAR_TOP"));
        assertEquals(0, FlagRegistry.parseCustom("  "));
    }

    @Test(expected = IllegalArgumentException.class)
    public void flags_parseCustomInvalid() {
        FlagRegistry.parseCustom("zzz");
    }

    @Test
    public void flags_describeAndNames() {
        int f = Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION;
        List<String> names = FlagRegistry.namesFor(f);
        assertTrue(names.contains("FLAG_ACTIVITY_NEW_TASK"));
        assertTrue(names.contains("FLAG_GRANT_READ_URI_PERMISSION"));
        assertEquals(0, FlagRegistry.unknownBits(f));
        assertTrue(FlagRegistry.describe(f).contains("NEW_TASK"));
        assertEquals("0", FlagRegistry.describe(0));
    }

    @Test
    public void flags_combineNamesAndCustom() {
        int f = FlagRegistry.combine(java.util.Arrays.asList("FLAG_ACTIVITY_NEW_TASK", "bogus"), "0x1");
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK | 1, f);
    }

    // ─── Value parsing ───────────────────────────────────────────────────

    @Test
    public void parseInt_decimalAndHex() {
        assertEquals(42, IntentBuilder.parseInt("42"));
        assertEquals(42, IntentBuilder.parseInt(" 0x2A "));
        assertEquals(-7, IntentBuilder.parseInt("-7"));
    }

    @Test
    public void parseBoolean_variants() {
        assertTrue(IntentBuilder.parseBoolean("true"));
        assertTrue(IntentBuilder.parseBoolean("YES"));
        assertTrue(IntentBuilder.parseBoolean("1"));
        assertFalse(IntentBuilder.parseBoolean("false"));
        assertFalse(IntentBuilder.parseBoolean("0"));
        assertFalse(IntentBuilder.parseBoolean("maybe"));
    }

    @Test
    public void parseBytes_hexCommaAndUtf8() {
        byte[] hex = IntentBuilder.parseBytes("0xDEADbeef");
        assertEquals(4, hex.length);
        assertEquals((byte) 0xDE, hex[0]);
        assertEquals((byte) 0xEF, hex[3]);
        byte[] csv = IntentBuilder.parseBytes("1, 2, 255");
        assertEquals(3, csv.length);
        assertEquals((byte) 255, csv[2]);
        byte[] utf = IntentBuilder.parseBytes("utf8:hi");
        assertEquals(2, utf.length);
        assertEquals('h', utf[0]);
        assertEquals(0, IntentBuilder.parseBytes("").length);
    }

    @Test(expected = NumberFormatException.class)
    public void parseBytes_oddHexFails() {
        IntentBuilder.parseBytes("0xABC");
    }

    @Test
    public void parseChar_unicodeEscape() {
        assertEquals('A', IntentBuilder.parseChar("\\u0041"));
        assertEquals('x', IntentBuilder.parseChar("xyz"));
    }

    @Test
    public void split_emptyYieldsEmptyArray() {
        assertEquals(0, IntentBuilder.splitTrimmed("").length);
        assertEquals(0, IntentBuilder.splitStrings("").length);
        String[] parts = IntentBuilder.splitTrimmed("a, b ,c");
        assertEquals(3, parts.length);
        assertEquals("b", parts[1]);
        String[] raw = IntentBuilder.splitStrings("a, b");
        assertEquals(" b", raw[1]);
    }

    // ─── Extra types ─────────────────────────────────────────────────────

    @Test
    public void extraTypes_flagsAndHints() {
        assertTrue(ExtraTypes.isContainer(ExtraTypes.BUNDLE));
        assertFalse(ExtraTypes.hasValue(ExtraTypes.NULL));
        assertTrue(ExtraTypes.hasValue(ExtraTypes.STRING));
        for (String t : ExtraTypes.ALL) {
            assertTrue("hint for " + t, !ExtraTypes.hintFor(t).isEmpty());
            assertTrue(ExtraTypes.isKnown(t));
        }
        assertFalse(ExtraTypes.isKnown("Nope"));
    }

    // ─── Model ───────────────────────────────────────────────────────────

    @Test
    public void model_modeNormalization() {
        assertEquals(IntentModel.MODE_ACTIVITY, IntentModel.normalizeMode("chipActivity"));
        assertEquals(IntentModel.MODE_FG_SERVICE, IntentModel.normalizeMode("chipFgService"));
        assertEquals(IntentModel.MODE_BIND_SERVICE, IntentModel.normalizeMode("bindService"));
        assertEquals(IntentModel.MODE_ACTIVITY, IntentModel.normalizeMode("garbage"));
        assertEquals(IntentModel.MODE_ACTIVITY, IntentModel.normalizeMode(null));
    }

    @Test
    public void model_extraCountIsRecursive() {
        IntentModel m = new IntentModel();
        IntentModel.ExtraEntry inner = new IntentModel.ExtraEntry("a", "1", ExtraTypes.INT);
        m.extras.add(IntentModel.ExtraEntry.bundle("b", java.util.Collections.singletonList(inner)));
        m.extras.add(new IntentModel.ExtraEntry("c", "x", ExtraTypes.STRING));
        assertEquals(3, m.extraCount());
    }

    @Test
    public void model_generateLabel() {
        IntentModel m = new IntentModel();
        m.actions.add("android.intent.action.VIEW");
        m.actions.add("android.intent.action.SEND");
        m.packageName = "com.example.app";
        m.componentName = "com.example.app.MainActivity";
        m.dataUri = "https://example.com";
        String label = m.generateLabel();
        assertTrue(label, label.startsWith("VIEW (+1) → com.example.app/MainActivity | https://example.com"));
    }
}
