package io.rudycity.tvbrowser;

import android.view.View;
import android.widget.ProgressBar;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;

import static org.junit.Assert.*;

/**
 * API 29 emulator smoke: verifies REAL Activity/Compose view wiring and progress
 * policy. These assertions run in the app process, not just a Gradle JVM.
 */
@RunWith(AndroidJUnit4.class)
public final class BrowserLaunchSmokeTest {
    @Rule public final ActivityScenarioRule<MainActivity> rule =
            new ActivityScenarioRule<MainActivity>(MainActivity.class);

    private static Object field(MainActivity activity, String name) throws Exception {
        Field f = MainActivity.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(activity);
    }

    @Test public void previewAttachesMaterialToolbarAndKeepsBrowserAlive() {
        rule.getScenario().onActivity(activity -> {
            try {
                BrowserChrome chrome = (BrowserChrome) field(activity, "composeChrome");
                BrowserEngine engine = (BrowserEngine) field(activity, "engine");
                assertNotNull("Preview on touch emulator must initialize Compose", chrome);
                assertNotNull("WebView engine must remain active", engine);
                assertNotNull("Browser surface must stay attached", engine.view().getParent());
                assertNotNull("Compose view must be attached", chrome.view().getParent());
                assertEquals(View.VISIBLE, chrome.view().getVisibility());
            } catch (Exception e) {
                throw new AssertionError("Compose startup smoke failed", e);
            }
        });
    }

    @Test public void materialThreeActionsAreVisibleAndInvokeOnlyExplicitSelection() {
        final AtomicInteger chosen = new AtomicInteger(-1);
        rule.getScenario().onActivity(activity -> {
            try {
                BrowserChrome chrome = (BrowserChrome) field(activity, "composeChrome");
                assertNotNull("Preview should use Compose", chrome);
                chrome.showActions("QA browser actions", new String[]{"First action", "Second action"},
                        new BrowserChrome.ChoiceHandler() {
                            @Override public void onChoice(int index) { chosen.set(index); }
                        });
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        });
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        assertTrue("Material 3 action dialog did not appear",
                device.wait(Until.hasObject(By.text("QA browser actions")), 7000));
        assertEquals("No actions may run without user selection", -1, chosen.get());
        assertTrue(device.wait(Until.hasObject(By.text("First action")), 5000));
        device.findObject(By.text("First action")).click();
        assertTrue(device.wait(Until.gone(By.text("QA browser actions")), 5000));
        assertEquals(0, chosen.get());
    }

    @Test public void modernFlavorBlocksCleartextAtAndroidNetworkLayer() {
        rule.getScenario().onActivity(activity -> {
            assertFalse(android.security.NetworkSecurityPolicy.getInstance()
                    .isCleartextTrafficPermitted("example.com"));
            assertFalse(BrowserNetworkPolicy.allowHttpForFlavor(BuildConfig.FLAVOR));
        });
    }

    @Test public void composeAndJavaLoadingProgressNeverAppearTogether() {
        rule.getScenario().onActivity(activity -> {
            try {
                BrowserChrome chrome = (BrowserChrome) field(activity, "composeChrome");
                assertNotNull(chrome);
                ProgressBar javaProgress = (ProgressBar) field(activity, "loadingProgress");
                BrowserEngine.Listener callbacks =
                        (BrowserEngine.Listener) field(activity, "rendererEvents");
                callbacks.onPageStarted("https://example.com/");
                assertEquals(View.GONE, javaProgress.getVisibility());
                callbacks.onProgress(50);
                assertEquals(View.GONE, javaProgress.getVisibility());
                callbacks.onProgress(100);
                assertEquals(View.GONE, javaProgress.getVisibility());
            } catch (Exception e) {
                throw new AssertionError("Duplicate progress indicator detected", e);
            }
        });
    }
}
