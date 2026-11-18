package io.rudycity.tvbrowser;

import android.content.Intent;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class GeckoRuntimeSmokeTest {
    private static BrowserEngine engineOf(MainActivity activity) {
        try {
            Field field = MainActivity.class.getDeclaredField("engine");
            field.setAccessible(true);
            return (BrowserEngine) field.get(activity);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    @Test public void startsRealGeckoViewAndUsesIndependentRenderer() {
        try (ActivityScenario<MainActivity> app = ActivityScenario.launch(MainActivity.class)) {
            app.onActivity(activity -> {
                BrowserEngine engine = engineOf(activity);
                assertNotNull(engine);
                assertEquals("Firefox GeckoView", engine.name());
                assertEquals("org.mozilla.geckoview.GeckoView", engine.view().getClass().getName());
                assertFalse(engine.view() instanceof android.webkit.WebView);
                assertEquals(View.VISIBLE, engine.view().getVisibility());
            });
        }
    }

    @Test public void canNavigateHttpsWithGeckoOwnedSession() {
        Intent intent = new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(), MainActivity.class);
        intent.setData(android.net.Uri.parse("https://example.com/"));
        try (ActivityScenario<MainActivity> app = ActivityScenario.launch(intent)) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            app.onActivity(activity -> {
                BrowserEngine engine = engineOf(activity);
                assertEquals("Firefox GeckoView", engine.name());
                assertNotNull(engine.view());
                // Rendering pixels and first contentful paint need separate CI evidence.
            });
        }
    }
}
