package io.rudycity.tvbrowser;

import android.content.SharedPreferences;

/** Persistent built-in add-on switches and exact-host site permission overrides. */
public final class ExtensionSettings {
    public static final String INHERIT = "inherit";
    public static final String ALLOW = "allow";
    public static final String DENY = "deny";

    private static final String ENABLED_PREFIX = "ext.enabled.";
    private static final String SITE_PREFIX = "ext.site.";
    private static final String SCRIPT_PREFIX = "ext.script.";
    public static final int MAX_SCRIPT_LENGTH = 32768;

    private final SharedPreferences prefs;

    public ExtensionSettings(SharedPreferences preferences) {
        prefs = preferences;
    }

    public boolean enabled(String id) {
        if (!ExtensionPolicy.knownId(id)) return false;
        boolean defaultValue = ExtensionPolicy.AD_BLOCKER.equals(id)
                || ExtensionPolicy.TV_NAVIGATION.equals(id);
        return prefs.getBoolean(ENABLED_PREFIX + id, defaultValue);
    }

    public void setEnabled(String id, boolean value) {
        if (!ExtensionPolicy.knownId(id)) throw new IllegalArgumentException("Unknown add-on");
        prefs.edit().putBoolean(ENABLED_PREFIX + id, value).apply();
    }

    public int enabledCount() {
        int total = 0;
        for (String id : ExtensionPolicy.IDS) if (enabled(id)) total++;
        return total;
    }

    public String siteRule(String id, String host) {
        if (!ExtensionPolicy.knownId(id) || host == null) return INHERIT;
        String value = prefs.getString(SITE_PREFIX + id + "." + host, INHERIT);
        return ALLOW.equals(value) || DENY.equals(value) ? value : INHERIT;
    }

    public void setSiteRule(String id, String host, String rule) {
        if (!ExtensionPolicy.knownId(id) || host == null
                || !host.equals(ExtensionPolicy.host("https://" + host)))
            throw new IllegalArgumentException("Invalid site permission");
        String key = SITE_PREFIX + id + "." + host;
        SharedPreferences.Editor editor = prefs.edit();
        if (ALLOW.equals(rule) || DENY.equals(rule)) editor.putString(key, rule);
        else if (INHERIT.equals(rule)) editor.remove(key);
        else throw new IllegalArgumentException("Unknown site permission");
        editor.apply();
    }

    /** Global disable always wins. Per-site DENY prevents execution. */
    public boolean enabledForUrl(String id, String url) {
        if (!enabled(id)) return false;
        String host = ExtensionPolicy.host(url);
        return host == null || !DENY.equals(siteRule(id, host));
    }

    public String scriptForUrl(String url) {
        if (!ExtensionPolicy.userscriptOrigin(url)
                || !enabledForUrl(ExtensionPolicy.USER_SCRIPT, url)) return "";
        String script = prefs.getString(SCRIPT_PREFIX + ExtensionPolicy.host(url), "");
        return script == null ? "" : script;
    }

    public String savedScript(String host) {
        if (host == null) return "";
        String script = prefs.getString(SCRIPT_PREFIX + host, "");
        return script == null ? "" : script;
    }

    public void setScript(String host, String script) {
        if (host == null || !host.equals(ExtensionPolicy.host("https://" + host)))
            throw new IllegalArgumentException("Invalid userscript host");
        if (script == null || script.length() > MAX_SCRIPT_LENGTH)
            throw new IllegalArgumentException("Script exceeds 32 KiB");
        SharedPreferences.Editor editor = prefs.edit();
        if (script.trim().isEmpty()) editor.remove(SCRIPT_PREFIX + host);
        else editor.putString(SCRIPT_PREFIX + host, script);
        editor.apply();
    }
}
