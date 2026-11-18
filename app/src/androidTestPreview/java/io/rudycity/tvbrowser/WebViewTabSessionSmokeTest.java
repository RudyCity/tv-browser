package io.rudycity.tvbrowser;

import android.view.View;
import android.webkit.WebView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Existing API21+ WebView flavor also receives real independent tab surfaces. */
@RunWith(AndroidJUnit4.class)
public final class WebViewTabSessionSmokeTest {
    private static Object field(MainActivity activity, String name) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(activity);
    }
    @Test public void backgroundTabIsIndependentAndCanBeClosed() {
        try (ActivityScenario<MainActivity> app = ActivityScenario.launch(MainActivity.class)) {
            app.onActivity(activity -> {
                try {
                    BrowserEngine first = (BrowserEngine) field(activity, "engine");
                    assertTrue(first.view() instanceof WebView);
                    Method create = MainActivity.class.getDeclaredMethod(
                            "createBrowserTab", String.class, String.class);
                    create.setAccessible(true);
                    assertTrue((Boolean) create.invoke(activity, "auto", null));
                    BrowserEngine second = (BrowserEngine) field(activity, "engine");
                    assertTrue(second.view() instanceof WebView);
                    assertNotSame(first, second);
                    assertNotSame(first.view(), second.view());
                    assertEquals(2, ((List<?>) field(activity, "tabs")).size());
                    assertEquals(View.GONE, first.view().getVisibility());
                    assertEquals(View.VISIBLE, second.view().getVisibility());
                    Method close = MainActivity.class.getDeclaredMethod(
                            "closeBrowserTab", int.class);
                    close.setAccessible(true);
                    close.invoke(activity, 1);
                    assertSame(first, field(activity, "engine"));
                    assertEquals(1, ((List<?>) field(activity, "tabs")).size());
                    assertEquals(View.VISIBLE, first.view().getVisibility());
                } catch (Exception ex) {
                    throw new AssertionError("Independent WebView tabs failed", ex);
                }
            });
        }
    }
}
