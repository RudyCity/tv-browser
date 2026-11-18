package io.rudycity.tvbrowser;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import io.rudycity.tvbrowser.gecko.GeckoEngine;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** Actual API29 Gecko sessions: private storage isolation and URL-only restart behavior. */
@RunWith(AndroidJUnit4.class)
public final class GeckoPrivacyAndPersistenceSmokeTest {
    private SharedPreferences prefs() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getSharedPreferences("tv_browser", Context.MODE_PRIVATE);
    }
    private void reset() {
        prefs().edit().remove("restorable_tab_urls")
                .remove("restorable_tab_selected").commit();
    }
    private static Object field(Object object, String fieldName) throws Exception {
        Field f = object.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        return f.get(object);
    }
    private static Object invoke(MainActivity activity, String method,
                                 Class<?>[] types, Object... args) throws Exception {
        Method m = MainActivity.class.getDeclaredMethod(method, types);
        m.setAccessible(true);
        return m.invoke(activity, args);
    }

    @Test public void geckoPrivateTabIsNeverWrittenToSavedInstanceOrDisk() {
        reset();
        try (ActivityScenario<MainActivity> app = ActivityScenario.launch(MainActivity.class)) {
            app.onActivity(activity -> {
                try {
                    assertTrue((Boolean) invoke(activity, "createBrowserTab",
                            new Class<?>[]{String.class, String.class, boolean.class},
                            "gecko", null, true));
                    BrowserEngine browser = (BrowserEngine) field(activity, "engine");
                    assertTrue(browser instanceof GeckoEngine);
                    assertTrue(((GeckoEngine) browser).isPrivateSession());

                    // Simulate a loaded private page without network dependence.
                    Object tab = field(activity, "activeTab");
                    Field url = tab.getClass().getDeclaredField("url");
                    url.setAccessible(true);
                    String sensitive = "https://private.example.test/secret";
                    url.set(tab, sensitive);
                    invoke(activity, "persistTabs", new Class<?>[]{});
                    assertFalse(prefs().getString("restorable_tab_urls", "").contains(sensitive));
                    Bundle state = new Bundle();
                    invoke(activity, "onSaveInstanceState", new Class<?>[]{Bundle.class}, state);
                    ArrayList<String> saved = state.getStringArrayList("restorable_tabs");
                    assertNotNull(saved);
                    assertFalse(saved.contains(sensitive));
                    assertNull(state.getString("current_url"));
                } catch (Exception error) {
                    throw new AssertionError("Gecko private tabs leaked state", error);
                }
            });
        } finally { reset(); }
    }

    @Test public void multipleNormalTabUrlsAreDurableAcrossFreshActivityLaunch() {
        reset();
        try {
            try (ActivityScenario<MainActivity> first = ActivityScenario.launch(MainActivity.class)) {
                first.onActivity(activity -> {
                    try {
                        assertTrue((Boolean) invoke(activity, "createBrowserTab",
                                new Class<?>[]{String.class, String.class}, "auto", null));
                        assertEquals(2, ((List<?>) field(activity, "tabs")).size());
                        assertEquals(2, new org.json.JSONArray(
                                prefs().getString("restorable_tab_urls", "[]")).length());
                    } catch (Exception error) { throw new AssertionError(error); }
                });
            }
            try (ActivityScenario<MainActivity> second = ActivityScenario.launch(MainActivity.class)) {
                second.onActivity(activity -> {
                    try {
                        assertEquals("Two real Gecko renderer sessions should be restored",
                                2, ((List<?>) field(activity, "tabs")).size());
                    } catch (Exception error) { throw new AssertionError(error); }
                });
            }
        } finally { reset(); }
    }
}
