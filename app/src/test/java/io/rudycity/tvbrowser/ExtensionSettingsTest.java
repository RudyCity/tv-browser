package io.rudycity.tvbrowser;

import android.content.SharedPreferences;

import org.junit.Test;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class ExtensionSettingsTest {
    @Test public void globalDisableCannotBeBypassedByAllowRule() {
        FakePrefs prefs = new FakePrefs();
        ExtensionSettings settings = new ExtensionSettings(prefs);
        assertTrue(settings.enabled(ExtensionPolicy.AD_BLOCKER));
        assertTrue(settings.enabled(ExtensionPolicy.VIDEO_TOOLS));
        settings.setSiteRule(ExtensionPolicy.AD_BLOCKER, "example.com", ExtensionSettings.ALLOW);
        assertTrue(settings.enabledForUrl(ExtensionPolicy.AD_BLOCKER, "https://example.com/path"));
        settings.setEnabled(ExtensionPolicy.AD_BLOCKER, false);
        assertFalse(settings.enabledForUrl(ExtensionPolicy.AD_BLOCKER, "https://example.com/path"));
    }

    @Test public void sitePermissionMatchesExactHostNotSuffixOrSubdomain() {
        ExtensionSettings settings = new ExtensionSettings(new FakePrefs());
        settings.setSiteRule(ExtensionPolicy.AD_BLOCKER, "example.com", ExtensionSettings.DENY);
        assertFalse(settings.enabledForUrl(ExtensionPolicy.AD_BLOCKER, "https://example.com/x"));
        assertTrue(settings.enabledForUrl(ExtensionPolicy.AD_BLOCKER, "https://a.example.com/x"));
        assertTrue(settings.enabledForUrl(ExtensionPolicy.AD_BLOCKER, "https://example.com.evil.test/x"));
        settings.setSiteRule(ExtensionPolicy.AD_BLOCKER, "example.com", ExtensionSettings.INHERIT);
        assertTrue(settings.enabledForUrl(ExtensionPolicy.AD_BLOCKER, "https://example.com/x"));
    }

    @Test public void scriptRequiresHttpsAndMatchingHostnameAndEnable() {
        ExtensionSettings settings = new ExtensionSettings(new FakePrefs());
        settings.setScript("example.com", "document.title = 'TV'");
        assertEquals("", settings.scriptForUrl("https://example.com/a"));
        settings.setEnabled(ExtensionPolicy.USER_SCRIPT, true);
        assertEquals("document.title = 'TV'", settings.scriptForUrl("https://example.com/a"));
        assertEquals("", settings.scriptForUrl("http://example.com/a"));
        assertEquals("", settings.scriptForUrl("https://sub.example.com/a"));
        settings.setSiteRule(ExtensionPolicy.USER_SCRIPT, "example.com", ExtensionSettings.DENY);
        assertEquals("", settings.scriptForUrl("https://example.com/a"));
    }

    @Test public void scriptLimitCountsUtf8BytesNotCharacters() {
        ExtensionSettings settings = new ExtensionSettings(new FakePrefs());
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 11000; i++) huge.append('\u00e9'); // 22,000 UTF-8 bytes
        settings.setScript("example.com", huge.toString());
        for (int i = 0; i < 6000; i++) huge.append('\u00e9'); // 34,000 UTF-8 bytes
        try {
            settings.setScript("example.com", huge.toString());
            fail("Expected UTF-8 byte limit");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("32 KiB"));
        }
    }

    @Test public void resetRestoresDefaultsButPreservesBrowserData() {
        FakePrefs prefs = new FakePrefs();
        prefs.edit().putString("history", "[{\"url\":\"https://example.com\"}]").commit();
        ExtensionSettings settings = new ExtensionSettings(prefs);
        settings.setEnabled(ExtensionPolicy.AD_BLOCKER, false);
        settings.setEnabled(ExtensionPolicy.USER_SCRIPT, true);
        settings.setSiteRule(ExtensionPolicy.DARK_MODE, "example.com", ExtensionSettings.DENY);
        settings.setScript("example.com", "document.title = 'A'");
        assertEquals(1, settings.savedScriptCount());
        settings.resetAll();
        assertEquals(0, settings.savedScriptCount());
        assertEquals(ExtensionSettings.INHERIT,
                settings.siteRule(ExtensionPolicy.DARK_MODE, "example.com"));
        assertTrue(settings.enabled(ExtensionPolicy.AD_BLOCKER));
        assertFalse(settings.enabled(ExtensionPolicy.USER_SCRIPT));
        assertTrue(prefs.getString("history", "").contains("example.com"));
    }

    @Test public void unknownExtensionCannotWriteSettings() {
        ExtensionSettings settings = new ExtensionSettings(new FakePrefs());
        assertFalse(settings.enabled("untrusted_crx"));
        try { settings.setEnabled("untrusted_crx", true); fail("Expected invalid ID"); }
        catch (IllegalArgumentException expected) { /* No arbitrary plugin IDs */ }
    }

    /** Small Java-only SharedPreferences fake; no Android runtime or emulator required. */
    private static final class FakePrefs implements SharedPreferences {
        private final Map<String, Object> data = new HashMap<String, Object>();

        @Override public Map<String, ?> getAll() {
            return new HashMap<String, Object>(data);
        }
        @Override public String getString(String key, String defValue) {
            Object value = data.get(key);
            return value instanceof String ? (String) value : defValue;
        }
        @Override @SuppressWarnings("unchecked")
        public Set<String> getStringSet(String key, Set<String> defValues) {
            Object value = data.get(key);
            return value instanceof Set ? (Set<String>) value : defValues;
        }
        @Override public int getInt(String key, int defValue) {
            Object value = data.get(key);
            return value instanceof Integer ? (Integer) value : defValue;
        }
        @Override public long getLong(String key, long defValue) {
            Object value = data.get(key);
            return value instanceof Long ? (Long) value : defValue;
        }
        @Override public float getFloat(String key, float defValue) {
            Object value = data.get(key);
            return value instanceof Float ? (Float) value : defValue;
        }
        @Override public boolean getBoolean(String key, boolean defValue) {
            Object value = data.get(key);
            return value instanceof Boolean ? (Boolean) value : defValue;
        }
        @Override public boolean contains(String key) { return data.containsKey(key); }
        @Override public Editor edit() { return new FakeEditor(); }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) { }
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) { }

        private final class FakeEditor implements Editor {
            private final Map<String, Object> changes = new HashMap<String, Object>();
            private final Set<String> removes = new HashSet<String>();
            private boolean clears;
            @Override public Editor putString(String k, String v) { changes.put(k, v); return this; }
            @Override public Editor putStringSet(String k, Set<String> v) {
                changes.put(k, v == null ? null : new HashSet<String>(v)); return this;
            }
            @Override public Editor putInt(String k, int v) { changes.put(k, v); return this; }
            @Override public Editor putLong(String k, long v) { changes.put(k, v); return this; }
            @Override public Editor putFloat(String k, float v) { changes.put(k, v); return this; }
            @Override public Editor putBoolean(String k, boolean v) { changes.put(k, v); return this; }
            @Override public Editor remove(String k) { removes.add(k); return this; }
            @Override public Editor clear() { clears = true; return this; }
            @Override public boolean commit() {
                if (clears) data.clear();
                for (String key : removes) data.remove(key);
                data.putAll(changes);
                return true;
            }
            @Override public void apply() { commit(); }
        }
    }
}
