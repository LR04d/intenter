package com.open.intenter;

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Tests that need real Intent / Bundle / JSON implementations. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class IntentBuilderRobolectricTest {

    private static IntentModel.ExtraEntry e(String key, String value, String type) {
        return new IntentModel.ExtraEntry(key, value, type);
    }

    @Test
    public void build_explicitComponentAndDataAndCategories() {
        IntentModel m = new IntentModel();
        m.useComponent = true;
        m.packageName = "com.example";
        m.componentName = ".Main";
        m.useAction = true;
        m.actions.add(Intent.ACTION_VIEW);
        m.useData = true;
        m.dataUri = "https://example.com/x";
        m.mimeType = "text/html";
        m.useCategory = true;
        m.categories.add(Intent.CATEGORY_BROWSABLE);
        m.useFlags = true;
        m.flagNames.add("FLAG_ACTIVITY_NEW_TASK");
        m.customFlags = "0x4";

        Intent i = IntentBuilder.buildFirst(m);
        assertEquals(new ComponentName("com.example", "com.example.Main"), i.getComponent());
        assertEquals(Intent.ACTION_VIEW, i.getAction());
        assertEquals("https://example.com/x", i.getDataString());
        assertEquals("text/html", i.getType());
        assertTrue(i.hasCategory(Intent.CATEGORY_BROWSABLE));
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK | 0x4, i.getFlags());
    }

    @Test
    public void build_packageOnlyWhenClassEmpty() {
        IntentModel m = new IntentModel();
        m.useComponent = true;
        m.packageName = "com.example";
        Intent i = IntentBuilder.buildFirst(m);
        assertNull(i.getComponent());
        assertEquals("com.example", i.getPackage());
    }

    @Test
    public void build_oneIntentPerAction() {
        IntentModel m = new IntentModel();
        m.useAction = true;
        m.actions.addAll(Arrays.asList("a.ONE", "", "a.TWO"));
        List<Intent> list = IntentBuilder.build(m);
        assertEquals(2, list.size());
        assertEquals("a.ONE", list.get(0).getAction());
        assertEquals("a.TWO", list.get(1).getAction());
    }

    @Test
    public void build_everyExtraType() {
        IntentModel m = new IntentModel();
        m.useExtras = true;
        m.extras.add(e("s", "hello", ExtraTypes.STRING));
        m.extras.add(e("cs", "seq", ExtraTypes.CHAR_SEQUENCE));
        m.extras.add(e("i", "0x10", ExtraTypes.INT));
        m.extras.add(e("l", "9999999999", ExtraTypes.LONG));
        m.extras.add(e("sh", "7", ExtraTypes.SHORT));
        m.extras.add(e("by", "-1", ExtraTypes.BYTE));
        m.extras.add(e("c", "\\u0041", ExtraTypes.CHAR));
        m.extras.add(e("b", "yes", ExtraTypes.BOOLEAN));
        m.extras.add(e("f", "1.5", ExtraTypes.FLOAT));
        m.extras.add(e("d", "2.5", ExtraTypes.DOUBLE));
        m.extras.add(e("u", "content://a/b", ExtraTypes.URI));
        m.extras.add(e("cn", "com.x/.Y", ExtraTypes.COMPONENT_NAME));
        m.extras.add(e("in", "intent:#Intent;action=a.B;S.k=v;end", ExtraTypes.INTENT));
        m.extras.add(e("n", "ignored", ExtraTypes.NULL));
        m.extras.add(e("sa", "a,b", ExtraTypes.STRING_ARRAY));
        m.extras.add(e("csa", "a,b", ExtraTypes.CHAR_SEQUENCE_ARRAY));
        m.extras.add(e("ia", "1, 2", ExtraTypes.INT_ARRAY));
        m.extras.add(e("la", "1,2", ExtraTypes.LONG_ARRAY));
        m.extras.add(e("sha", "1,2", ExtraTypes.SHORT_ARRAY));
        m.extras.add(e("ba", "0x0102", ExtraTypes.BYTE_ARRAY));
        m.extras.add(e("ca", "xy", ExtraTypes.CHAR_ARRAY));
        m.extras.add(e("za", "true,false", ExtraTypes.BOOLEAN_ARRAY));
        m.extras.add(e("fa", "1.5,2", ExtraTypes.FLOAT_ARRAY));
        m.extras.add(e("da", "1.5,2", ExtraTypes.DOUBLE_ARRAY));
        m.extras.add(e("ua", "content://a,content://b", ExtraTypes.URI_ARRAY));
        m.extras.add(e("sl", "a,b", ExtraTypes.STRING_LIST));
        m.extras.add(e("il", "1,2", ExtraTypes.INT_LIST));
        m.extras.add(e("csl", "a,b", ExtraTypes.CHAR_SEQUENCE_LIST));
        m.extras.add(e("ul", "content://a", ExtraTypes.URI_LIST));
        m.extras.add(e("empty", "", ExtraTypes.INT_ARRAY));
        List<IntentModel.ExtraEntry> inner = new ArrayList<>();
        inner.add(e("x", "1", ExtraTypes.INT));
        List<IntentModel.ExtraEntry> innerMost = new ArrayList<>();
        innerMost.add(e("deep", "true", ExtraTypes.BOOLEAN));
        inner.add(IntentModel.ExtraEntry.bundle("nested", innerMost));
        m.extras.add(IntentModel.ExtraEntry.bundle("bundle", inner));
        m.extras.add(e("", "no key", ExtraTypes.STRING));

        Intent i = IntentBuilder.buildFirst(m);
        Bundle b = i.getExtras();
        assertNotNull(b);
        assertEquals("hello", b.getString("s"));
        assertEquals("seq", b.getCharSequence("cs").toString());
        assertEquals(16, b.getInt("i"));
        assertEquals(9999999999L, b.getLong("l"));
        assertEquals((short) 7, b.getShort("sh"));
        assertEquals((byte) -1, b.getByte("by"));
        assertEquals('A', b.getChar("c"));
        assertTrue(b.getBoolean("b"));
        assertEquals(1.5f, b.getFloat("f"), 0f);
        assertEquals(2.5, b.getDouble("d"), 0.0);
        assertEquals(Uri.parse("content://a/b"), b.getParcelable("u"));
        assertEquals(new ComponentName("com.x", "com.x.Y"), b.getParcelable("cn"));
        Intent nestedIntent = b.getParcelable("in");
        assertNotNull(nestedIntent);
        assertEquals("a.B", nestedIntent.getAction());
        assertEquals("v", nestedIntent.getStringExtra("k"));
        assertTrue(b.containsKey("n"));
        assertNull(b.get("n"));
        assertArrayEquals(new String[]{"a", "b"}, b.getStringArray("sa"));
        assertEquals(2, b.getCharSequenceArray("csa").length);
        assertArrayEquals(new int[]{1, 2}, b.getIntArray("ia"));
        assertArrayEquals(new long[]{1, 2}, b.getLongArray("la"));
        assertArrayEquals(new short[]{1, 2}, b.getShortArray("sha"));
        assertArrayEquals(new byte[]{1, 2}, b.getByteArray("ba"));
        assertArrayEquals(new char[]{'x', 'y'}, b.getCharArray("ca"));
        assertArrayEquals(new boolean[]{true, false}, b.getBooleanArray("za"));
        assertArrayEquals(new float[]{1.5f, 2f}, b.getFloatArray("fa"), 0f);
        assertArrayEquals(new double[]{1.5, 2}, b.getDoubleArray("da"), 0.0);
        assertEquals(2, b.getParcelableArray("ua").length);
        assertEquals(Arrays.asList("a", "b"), b.getStringArrayList("sl"));
        assertEquals(Arrays.asList(1, 2), b.getIntegerArrayList("il"));
        assertEquals(2, b.getCharSequenceArrayList("csl").size());
        assertEquals(1, b.getParcelableArrayList("ul").size());
        assertEquals(0, b.getIntArray("empty").length);
        Bundle nested = b.getBundle("bundle");
        assertNotNull(nested);
        assertEquals(1, nested.getInt("x"));
        assertTrue(nested.getBundle("nested").getBoolean("deep"));
        assertFalse(b.containsKey(""));
    }

    @Test(expected = IntentBuilder.BuildException.class)
    public void build_invalidNumberThrowsWithKey() {
        IntentModel m = new IntentModel();
        m.useExtras = true;
        m.extras.add(e("count", "abc", ExtraTypes.INT));
        IntentBuilder.buildFirst(m);
    }

    @Test
    public void build_clipData() {
        IntentModel m = new IntentModel();
        m.useClipData = true;
        m.clipDataLabel = "lbl";
        m.clipDataMimeTypes.add("text/plain");
        m.clipDataItems.add(new IntentModel.ClipDataItem("Text", "hello"));
        m.clipDataItems.add(new IntentModel.ClipDataItem("Uri", "content://x/1"));
        m.clipDataItems.add(new IntentModel.ClipDataItem("Intent", "intent:#Intent;action=a.B;end"));
        Intent i = IntentBuilder.buildFirst(m);
        assertNotNull(i.getClipData());
        assertEquals(3, i.getClipData().getItemCount());
        assertEquals("hello", i.getClipData().getItemAt(0).getText().toString());
        assertEquals(Uri.parse("content://x/1"), i.getClipData().getItemAt(1).getUri());
        assertEquals("a.B", i.getClipData().getItemAt(2).getIntent().getAction());
    }

    @Test
    public void codec_uriRoundTrip() throws Exception {
        IntentModel m = new IntentModel();
        m.useAction = true;
        m.actions.add(Intent.ACTION_SEND);
        m.useData = true;
        m.mimeType = "text/plain";
        m.useExtras = true;
        m.extras.add(e("android.intent.extra.TEXT", "hi there", ExtraTypes.STRING));
        m.extras.add(e("n", "5", ExtraTypes.INT));
        m.useFlags = true;
        m.flagNames.add("FLAG_ACTIVITY_NEW_TASK");
        Intent i = IntentBuilder.buildFirst(m);
        String uri = i.toUri(Intent.URI_INTENT_SCHEME);

        IntentModel back = IntentCodec.fromUri(uri);
        assertEquals(Intent.ACTION_SEND, back.primaryAction());
        assertEquals("text/plain", back.mimeType);
        assertTrue(back.useExtras);
        assertEquals(2, back.extras.size());
        assertTrue(back.flagNames.contains("FLAG_ACTIVITY_NEW_TASK"));
        Intent rebuilt = IntentBuilder.buildFirst(back);
        assertEquals("hi there", rebuilt.getStringExtra("android.intent.extra.TEXT"));
        assertEquals(5, rebuilt.getIntExtra("n", 0));
    }

    @Test
    public void codec_fromIntentHandlesNestedBundle() {
        Intent i = new Intent("a.B");
        Bundle inner = new Bundle();
        inner.putLong("id", 7L);
        inner.putStringArrayList("names", new ArrayList<>(Arrays.asList("x", "y")));
        i.putExtra("payload", inner);
        i.putExtra("bytes", new byte[]{1, 2});
        i.putExtra("nothing", (String) null);
        IntentModel m = IntentCodec.fromIntent(i);
        assertEquals(3, m.extras.size());
        IntentModel.ExtraEntry payload = null, bytes = null, nothing = null;
        for (IntentModel.ExtraEntry x : m.extras) {
            if (x.key.equals("payload")) payload = x;
            if (x.key.equals("bytes")) bytes = x;
            if (x.key.equals("nothing")) nothing = x;
        }
        assertNotNull(payload);
        assertEquals(ExtraTypes.BUNDLE, payload.type);
        assertEquals(2, payload.children.size());
        assertEquals(ExtraTypes.BYTE_ARRAY, bytes.type);
        assertEquals("0x0102", bytes.value);
        assertEquals(ExtraTypes.NULL, nothing.type);
        // and it rebuilds equivalently
        Intent rebuilt = IntentBuilder.buildFirst(m);
        assertEquals(7L, rebuilt.getBundleExtra("payload").getLong("id"));
        assertArrayEquals(new byte[]{1, 2}, rebuilt.getByteArrayExtra("bytes"));
    }

    @Test
    public void codec_amCommandRoundTrip() {
        IntentModel m = IntentCodec.fromAm("adb shell am broadcast -a com.x.ACTION -n 'com.x/.Rcv' --es key 'a b' --ei n 3 --ez ok true --eia arr 1,2 --esn nul -f 0x10000000 --receiver-permission com.x.PERM");
        assertEquals(IntentModel.MODE_BROADCAST, m.launchType);
        assertEquals("com.x.ACTION", m.primaryAction());
        assertEquals("com.x", m.packageName);
        assertEquals("com.x.Rcv", m.componentName);
        assertEquals(5, m.extras.size());
        assertEquals("a b", m.extras.get(0).value);
        assertEquals(ExtraTypes.INT, m.extras.get(1).type);
        assertEquals(ExtraTypes.BOOLEAN, m.extras.get(2).type);
        assertEquals(ExtraTypes.INT_ARRAY, m.extras.get(3).type);
        assertEquals(ExtraTypes.NULL, m.extras.get(4).type);
        assertTrue(m.flagNames.contains("FLAG_ACTIVITY_NEW_TASK"));
        assertEquals("com.x.PERM", m.permission);

        String adb = IntentCodec.toAdb(m, IntentBuilder.buildFirst(m));
        assertTrue(adb, adb.startsWith("adb shell am broadcast --receiver-permission com.x.PERM -a com.x.ACTION"));
        assertTrue(adb, adb.contains("-n com.x/.Rcv"));
        assertTrue(adb, adb.contains("--es key 'a b'"));
        assertTrue(adb, adb.contains("--ei n 3"));
        assertTrue(adb, adb.contains("--esn nul"));
        assertTrue(adb, adb.contains("-f 0x10000000"));
    }

    @Test
    public void codec_amPositionalArguments() {
        IntentModel comp = IntentCodec.fromAm("am start com.x/.Main");
        assertEquals("com.x", comp.packageName);
        assertEquals("com.x.Main", comp.componentName);
        IntentModel data = IntentCodec.fromAm("am start -a android.intent.action.VIEW https://e.com");
        assertEquals("https://e.com", data.dataUri);
        IntentModel pkg = IntentCodec.fromAm("am start-foreground-service -p com.x -a X");
        assertEquals(IntentModel.MODE_FG_SERVICE, pkg.launchType);
        assertEquals("com.x", pkg.packageName);
    }

    @Test
    public void codec_importAnyDetectsFormats() throws Exception {
        assertEquals("a.B", IntentCodec.importAny("intent:#Intent;action=a.B;end").primaryAction());
        assertEquals("a.B", IntentCodec.importAny("#Intent;action=a.B;end").primaryAction());
        assertEquals(IntentModel.MODE_SERVICE, IntentCodec.importAny("am startservice -n com.x/.S").launchType);
        IntentModel plain = IntentCodec.importAny("https://example.com/path");
        assertEquals("https://example.com/path", plain.dataUri);
        IntentModel m = new IntentModel();
        m.name = "n";
        assertEquals("n", IntentCodec.importAny(m.toJsonString()).name);
    }

    @Test
    public void codec_javaAndKotlinContainEssentials() {
        IntentModel m = new IntentModel();
        m.useComponent = true;
        m.packageName = "com.x";
        m.componentName = ".Main";
        m.useAction = true;
        m.actions.add("a.B");
        m.useExtras = true;
        List<IntentModel.ExtraEntry> inner = new ArrayList<>();
        inner.add(e("id", "1", ExtraTypes.INT));
        m.extras.add(IntentModel.ExtraEntry.bundle("payload", inner));
        m.extras.add(e("flag", "true", ExtraTypes.BOOLEAN));
        m.launchType = IntentModel.MODE_ACTIVITY_RESULT;
        String java = IntentCodec.toJava(m);
        assertTrue(java, java.contains("new Intent(\"a.B\")"));
        assertTrue(java, java.contains("intent.setClassName(\"com.x\", \"com.x.Main\")"));
        assertTrue(java, java.contains("bundle1.putInt(\"id\", 1)"));
        assertTrue(java, java.contains("intent.putExtra(\"payload\", bundle1)"));
        assertTrue(java, java.contains("startActivityForResult(intent, 1001)"));
        String kotlin = IntentCodec.toKotlin(m);
        assertTrue(kotlin, kotlin.contains("val intent = Intent(\"a.B\")"));
        assertTrue(kotlin, kotlin.contains("val bundle1 = Bundle()"));
    }

    @Test
    public void model_jsonRoundTripWithNestedExtrasAndLegacyBundles() throws Exception {
        IntentModel m = new IntentModel();
        m.useExtras = true;
        List<IntentModel.ExtraEntry> inner = new ArrayList<>();
        inner.add(e("id", "1", ExtraTypes.INT));
        m.extras.add(IntentModel.ExtraEntry.bundle("payload", inner));
        m.launchType = IntentModel.MODE_ORDERED_BROADCAST;
        m.useAdvanced = true;
        m.permission = "p";
        m.name = "fav";
        IntentModel back = IntentModel.fromJsonString(m.toJsonString());
        assertEquals("fav", back.name);
        assertEquals(IntentModel.MODE_ORDERED_BROADCAST, back.launchType);
        assertEquals("p", back.permission);
        assertEquals(1, back.extras.size());
        assertEquals(ExtraTypes.BUNDLE, back.extras.get(0).type);
        assertEquals("id", back.extras.get(0).children.get(0).key);

        // Legacy v1 format with a separate bundles section and chip launch type
        String legacy = "{\"useBundle\":true,\"launchType\":\"chipFgService\",\"bundles\":[{\"key\":\"b\",\"extras\":[{\"key\":\"k\",\"value\":\"v\",\"type\":\"String\"}]},{\"key\":\"\",\"extras\":[{\"key\":\"top\",\"value\":\"1\",\"type\":\"Integer\"}]}]}";
        IntentModel migrated = IntentModel.fromJsonString(legacy);
        assertTrue(migrated.useExtras);
        assertEquals(IntentModel.MODE_FG_SERVICE, migrated.launchType);
        assertEquals(2, migrated.extras.size());
        assertEquals(ExtraTypes.BUNDLE, migrated.extras.get(0).type);
        assertEquals("top", migrated.extras.get(1).key);
    }

    @Test
    public void extrasEditor_jsonToEntries() throws Exception {
        List<IntentModel.ExtraEntry> list = ExtrasEditor.entriesFromJson(
                "{\"i\":1,\"l\":99999999999,\"d\":1.5,\"b\":true,\"s\":\"x\",\"n\":null,\"o\":{\"k\":2},\"ia\":[1,2],\"sa\":[\"a\",\"b\"],\"ba\":[true]}");
        assertEquals(10, list.size());
        java.util.Map<String, IntentModel.ExtraEntry> byKey = new java.util.HashMap<>();
        for (IntentModel.ExtraEntry x : list) byKey.put(x.key, x);
        assertEquals(ExtraTypes.INT, byKey.get("i").type);
        assertEquals(ExtraTypes.LONG, byKey.get("l").type);
        assertEquals(ExtraTypes.DOUBLE, byKey.get("d").type);
        assertEquals(ExtraTypes.BOOLEAN, byKey.get("b").type);
        assertEquals(ExtraTypes.STRING, byKey.get("s").type);
        assertEquals(ExtraTypes.NULL, byKey.get("n").type);
        assertEquals(ExtraTypes.BUNDLE, byKey.get("o").type);
        assertEquals(ExtraTypes.INT_ARRAY, byKey.get("ia").type);
        assertEquals(ExtraTypes.STRING_ARRAY, byKey.get("sa").type);
        assertEquals(ExtraTypes.BOOLEAN_ARRAY, byKey.get("ba").type);
    }

    @Test
    public void dumper_describesTypes() {
        Intent i = new Intent("a.B");
        i.setData(Uri.parse("https://x"));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.putExtra("s", "v");
        i.putExtra("arr", new int[]{1, 2});
        Bundle nested = new Bundle();
        nested.putBoolean("ok", true);
        i.putExtra("b", nested);
        String dump = IntentDumper.dump(i);
        assertTrue(dump, dump.contains("Action: a.B"));
        assertTrue(dump, dump.contains("Data: https://x"));
        assertTrue(dump, dump.contains("NEW_TASK"));
        assertTrue(dump, dump.contains("s (String) = \"v\""));
        assertTrue(dump, dump.contains("arr (int[2]) = [1, 2]"));
        assertTrue(dump, dump.contains("ok (boolean) = true"));
        assertEquals("a.B https://x", IntentDumper.summary(i));
    }

    @Test
    public void savedStore_presetsBuild() {
        for (IntentModel p : SavedStore.presets()) {
            assertFalse(p.name.isEmpty());
            Intent i = IntentBuilder.buildFirst(p);
            assertFalse(p.name, IntentBuilder.isBlank(i));
            JSONObject json = null;
            try { json = p.toJson(); } catch (Exception e) { throw new AssertionError(e); }
            assertNotNull(json);
        }
    }
}
