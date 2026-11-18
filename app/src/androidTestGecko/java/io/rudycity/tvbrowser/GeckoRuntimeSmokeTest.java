package io.rudycity.tvbrowser;

import android.content.Intent;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
            });
            // Gecko's content renderer must produce actual page pixels, not merely construct
            // GeckoView or show an unrelated Compose home screen.
            boolean painted = false;
            for (int attempt = 0; attempt < 150; attempt++) {
                final AtomicBoolean captured = new AtomicBoolean(false);
                app.onActivity(activity -> {
                    BrowserEngine engine = engineOf(activity);
                    try {
                        Object result = engine.getClass()
                                .getMethod("hasFirstContentfulPaint").invoke(engine);
                        captured.set(Boolean.TRUE.equals(result));
                    } catch (ReflectiveOperationException failure) {
                        throw new AssertionError(failure);
                    }
                });
                if (captured.get()) { painted = true; break; }
                android.os.SystemClock.sleep(250);
            }
            assertTrue("Expected Gecko's real first contentful paint of example.com", painted);
        }
    }

    @Test public void installsAndListsRealBundledFirefoxExtension() throws Exception {
        try (ActivityScenario<MainActivity> app = ActivityScenario.launch(MainActivity.class)) {
            final CountDownLatch installed = new CountDownLatch(1);
            final AtomicBoolean success = new AtomicBoolean(false);
            app.onActivity(activity -> {
                BrowserEngine engine = engineOf(activity);
                assertTrue("Gecko must expose the real WebExtensions controller",
                        engine instanceof BrowserExtensions);
                ((BrowserExtensions) engine).installBundledTestAddon((ok, message) -> {
                    success.set(ok);
                    installed.countDown();
                });
            });
            assertTrue("Timed out waiting for Mozilla's extension install callback",
                    installed.await(45, TimeUnit.SECONDS));
            assertTrue("Mozilla rejected bundled Firefox extension", success.get());

            final CountDownLatch listed = new CountDownLatch(1);
            final AtomicBoolean discovered = new AtomicBoolean(false);
            app.onActivity(activity ->
                    ((BrowserExtensions) engineOf(activity)).listAddons((addOns, error) -> {
                        for (BrowserExtensions.AddonInfo addOn : addOns) {
                            if ("rudy-page-marker@rudycity.browser".equals(addOn.id)) {
                                discovered.set(true);
                            }
                        }
                        listed.countDown();
                    }));
            assertTrue("Timed out querying Mozilla WebExtensionController.list",
                    listed.await(30, TimeUnit.SECONDS));
            assertTrue("Bundled Firefox WebExtension missing from Gecko's installed list",
                    discovered.get());
        }
    }
}
