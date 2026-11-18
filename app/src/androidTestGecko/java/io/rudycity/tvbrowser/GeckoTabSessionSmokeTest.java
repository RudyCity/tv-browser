package io.rudycity.tvbrowser;

import android.view.View;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** A tab is a real independent GeckoSession renderer, not a displayed counter. */
@RunWith(AndroidJUnit4.class)
public final class GeckoTabSessionSmokeTest {
    private static Object field(MainActivity activity, String name) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(activity);
    }
    private static Object invoke(MainActivity activity, String name,
                                 Class<?>[] types, Object... args) throws Exception {
        Method method = MainActivity.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(activity, args);
    }

    @Test public void newTabOwnsSeparateGeckoViewAndSwitchKeepsPreviousSession() {
        try (ActivityScenario<MainActivity> app = ActivityScenario.launch(MainActivity.class)) {
            app.onActivity(activity -> {
                try {
                    BrowserEngine first = (BrowserEngine) field(activity, "engine");
                    assertEquals("Firefox GeckoView", first.name());
                    assertTrue((Boolean) invoke(activity, "createBrowserTab",
                            new Class<?>[]{String.class, String.class}, "auto", null));
                    BrowserEngine second = (BrowserEngine) field(activity, "engine");
                    assertNotSame("Each tab requires its own renderer/session", first, second);
                    assertNotSame(first.view(), second.view());
                    assertEquals(View.GONE, first.view().getVisibility());
                    assertEquals(View.VISIBLE, second.view().getVisibility());
                    assertEquals(2, ((List<?>) field(activity, "tabs")).size());

                    invoke(activity, "switchBrowserTab", new Class<?>[]{int.class}, 0);
                    assertSame("Switch must preserve original renderer, not reload a URL",
                            first, field(activity, "engine"));
                    assertEquals(View.GONE, second.view().getVisibility());
                    assertEquals(View.VISIBLE, first.view().getVisibility());
                    invoke(activity, "closeBrowserTab", new Class<?>[]{int.class}, 1);
                    assertEquals(1, ((List<?>) field(activity, "tabs")).size());
                    assertSame(first, field(activity, "engine"));
                } catch (Exception exception) {
                    throw new AssertionError("Real Gecko tab lifecycle failed", exception);
                }
            });
        }
    }
}
