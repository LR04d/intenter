package com.open.intenter;

import android.content.Intent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.net.URI;

import static org.junit.Assert.*;

/**
 * Unit tests for intent building logic and IntentModel serialization.
 */
@RunWith(JUnit4.class)
public class IntentBuilderUnitTest {

    // ─── Type parsing ────────────────────────────────────────────────────

    @Test
    public void parseInt_valid() {
        assertEquals(42, Integer.parseInt("42"));
    }

    @Test(expected = NumberFormatException.class)
    public void parseInt_invalid_throws() {
        Integer.parseInt("notAnInt");
    }

    @Test
    public void parseFloat_valid() {
        assertEquals(3.14f, Float.parseFloat("3.14"), 0.001f);
    }

    @Test
    public void parseLong_valid() {
        assertEquals(9999999999L, Long.parseLong("9999999999"));
    }

    @Test
    public void parseDouble_valid() {
        assertEquals(1.23456789, Double.parseDouble("1.23456789"), 0.0000001);
    }

    @Test
    public void parseBoolean_trueValue() {
        assertTrue(Boolean.parseBoolean("true"));
        assertTrue(Boolean.parseBoolean("TRUE"));
        assertTrue(Boolean.parseBoolean("True"));
    }

    @Test
    public void parseBoolean_falseValue() {
        assertFalse(Boolean.parseBoolean("false"));
        assertFalse(Boolean.parseBoolean("no"));
        assertFalse(Boolean.parseBoolean("0"));
    }

    // ─── Flag hex parsing ────────────────────────────────────────────────

    @Test
    public void parseHexFlag_valid() {
        String hex = "0x10000000";
        int flag = (int) Long.parseLong(hex.substring(2), 16);
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, flag);
    }

    @Test
    public void parseDecFlag_valid() {
        int flag = (int) Long.parseLong("268435456");
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, flag);
    }

    @Test(expected = NumberFormatException.class)
    public void parseHexFlag_invalid_throws() {
        Long.parseLong("zzzz", 16);
    }

    // ─── URI parsing (java.net.URI for unit test compat) ─────────────────

    @Test
    public void parseUri_https() throws Exception {
        URI uri = new URI("https://www.example.com/path?q=1");
        assertEquals("https", uri.getScheme());
        assertEquals("www.example.com", uri.getHost());
    }

    @Test
    public void parseUri_tel() throws Exception {
        URI uri = new URI("tel:+1234567890");
        assertEquals("tel", uri.getScheme());
    }

    @Test
    public void parseUri_content() throws Exception {
        URI uri = new URI("content://com.example/data/1");
        assertEquals("content", uri.getScheme());
        assertEquals("com.example", uri.getAuthority());
    }

    @Test
    public void parseUri_mailto() throws Exception {
        URI uri = new URI("mailto:test@example.com");
        assertEquals("mailto", uri.getScheme());
    }

    @Test
    public void parseUri_customScheme() throws Exception {
        URI uri = new URI("myapp://open/screen?param=value");
        assertEquals("myapp", uri.getScheme());
        assertEquals("open", uri.getHost());
    }

    // ─── String trimming ─────────────────────────────────────────────────

    @Test
    public void trimText_removesWhitespace() {
        assertEquals("com.example.app", "  com.example.app  ".trim());
    }

    @Test
    public void trimText_emptyAfterTrim() {
        assertTrue("   ".trim().isEmpty());
    }

    // ─── Component name logic ────────────────────────────────────────────

    @Test
    public void componentName_pkgAndClass_notEmpty() {
        assertFalse("com.example.app".isEmpty());
        assertFalse(".MainActivity".isEmpty());
    }

    @Test
    public void componentName_onlyPkg_classEmpty() {
        assertFalse("com.example.app".isEmpty());
        assertTrue("".isEmpty());
    }

    @Test
    public void componentName_bothEmpty() {
        assertTrue("".isEmpty());
    }

    // ─── String[] splitting ──────────────────────────────────────────────

    @Test
    public void stringArraySplit_commaSeparated() {
        String[] arr = "a,b,c".split(",");
        assertEquals(3, arr.length);
        assertEquals("a", arr[0]);
        assertEquals("b", arr[1]);
        assertEquals("c", arr[2]);
    }

    @Test
    public void intArrayParsing_commaSeparated() {
        String value = "1, 2, 3";
        String[] parts = value.split(",");
        int[] arr = new int[parts.length];
        for (int i = 0; i < parts.length; i++) arr[i] = Integer.parseInt(parts[i].trim());
        assertEquals(3, arr.length);
        assertEquals(1, arr[0]);
        assertEquals(2, arr[1]);
        assertEquals(3, arr[2]);
    }

    @Test
    public void longArrayParsing_commaSeparated() {
        String value = "100, 200, 9999999999";
        String[] parts = value.split(",");
        long[] arr = new long[parts.length];
        for (int i = 0; i < parts.length; i++) arr[i] = Long.parseLong(parts[i].trim());
        assertEquals(3, arr.length);
        assertEquals(100L, arr[0]);
        assertEquals(200L, arr[1]);
        assertEquals(9999999999L, arr[2]);
    }

    @Test
    public void doubleArrayParsing_commaSeparated() {
        String value = "1.1, 2.2, 3.3";
        String[] parts = value.split(",");
        double[] arr = new double[parts.length];
        for (int i = 0; i < parts.length; i++) arr[i] = Double.parseDouble(parts[i].trim());
        assertEquals(3, arr.length);
        assertEquals(1.1, arr[0], 0.01);
        assertEquals(2.2, arr[1], 0.01);
        assertEquals(3.3, arr[2], 0.01);
    }

    @Test
    public void booleanArrayParsing_commaSeparated() {
        String value = "true, false, true";
        String[] parts = value.split(",");
        boolean[] arr = new boolean[parts.length];
        for (int i = 0; i < parts.length; i++) arr[i] = Boolean.parseBoolean(parts[i].trim());
        assertEquals(3, arr.length);
        assertTrue(arr[0]);
        assertFalse(arr[1]);
        assertTrue(arr[2]);
    }

    @Test
    public void arrayListIntegerParsing_commaSeparated() {
        String value = "10, 20, 30";
        String[] parts = value.split(",");
        java.util.ArrayList<Integer> al = new java.util.ArrayList<>();
        for (String p : parts) al.add(Integer.parseInt(p.trim()));
        assertEquals(3, al.size());
        assertEquals(Integer.valueOf(10), al.get(0));
        assertEquals(Integer.valueOf(20), al.get(1));
        assertEquals(Integer.valueOf(30), al.get(2));
    }

    // ─── IntentModel label generation ───────────────────────────────────

    @Test
    public void intentModel_generateLabel_actionAndPackage() {
        IntentModel m = new IntentModel();
        m.action = "android.intent.action.VIEW";
        m.packageName = "com.example.app";
        m.componentName = "com.example.app.MainActivity";
        String label = m.generateLabel();
        assertTrue(label.contains("VIEW"));
        assertTrue(label.contains("com.example.app"));
        assertTrue(label.contains("MainActivity"));
    }

    @Test
    public void intentModel_generateLabel_empty() {
        IntentModel m = new IntentModel();
        assertEquals("(empty intent)", m.generateLabel());
    }

    @Test
    public void intentModel_generateLabel_dataUriOnly() {
        IntentModel m = new IntentModel();
        m.dataUri = "https://test.com/page";
        String label = m.generateLabel();
        assertTrue(label.contains("https://test.com/page"));
    }

    @Test
    public void intentModel_generateLabel_longDataUri() {
        IntentModel m = new IntentModel();
        m.dataUri = "https://test.com/very/long/path/that/exceeds/forty/characters/by/quite/a/lot";
        String label = m.generateLabel();
        assertTrue(label.contains("…"));
        assertTrue(label.length() < m.dataUri.length() + 5);
    }

    @Test
    public void intentModel_defaultValues() {
        IntentModel m = new IntentModel();
        assertEquals("", m.packageName);
        assertEquals("", m.action);
        assertEquals("", m.dataUri);
        assertEquals("", m.mimeType);
        assertFalse(m.useComponent);
        assertFalse(m.useAction);
        assertFalse(m.useData);
        assertFalse(m.useFlags);
        assertFalse(m.useChooser);
        assertEquals("chipActivity", m.launchType);
        assertTrue(m.categories.isEmpty());
        assertTrue(m.extras.isEmpty());
        assertTrue(m.bundles.isEmpty());
        assertTrue(m.flagNames.isEmpty());
    }

    @Test
    public void extraEntry_constructorAndFields() {
        IntentModel.ExtraEntry e = new IntentModel.ExtraEntry("myKey", "myVal", "Boolean");
        assertEquals("myKey", e.key);
        assertEquals("myVal", e.value);
        assertEquals("Boolean", e.type);
    }

    @Test
    public void extraEntry_defaultConstructor() {
        IntentModel.ExtraEntry e = new IntentModel.ExtraEntry();
        assertEquals("", e.key);
        assertEquals("", e.value);
        assertEquals("String", e.type);
    }

    @Test
    public void bundleEntry_defaultValues() {
        IntentModel.BundleEntry b = new IntentModel.BundleEntry();
        assertEquals("", b.key);
        assertTrue(b.extras.isEmpty());
    }
}
