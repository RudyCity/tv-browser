package io.rudycity.tvbrowser;

import android.view.View;
import android.widget.ProgressBar;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.webkit.WebView;
import android.os.SystemClock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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


    @Test public void localHomePageHasDomTextAndVisibleWebContent() throws Exception {
        final AtomicReference<WebView> browser = new AtomicReference<>();
        rule.getScenario().onActivity(activity -> {
            try {
                BrowserEngine engine = (BrowserEngine) field(activity, "engine");
                assertTrue("Preview must be backed by WebView", engine.view() instanceof WebView);
                browser.set((WebView) engine.view());
            } catch (Exception ex) {
                throw new AssertionError(ex);
            }
        });
        final android.app.Instrumentation instrumentation =
                InstrumentationRegistry.getInstrumentation();
        String text = "";
        long deadline = SystemClock.elapsedRealtime() + 12000;
        while (SystemClock.elapsedRealtime() < deadline) {
            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicReference<String> dom = new AtomicReference<>("");
            instrumentation.runOnMainSync(() -> browser.get().evaluateJavascript(
                    "document.body ? document.body.innerText : ''",
                    value -> { dom.set(value); latch.countDown(); }));
            assertTrue("WebView JavaScript callback timed out", latch.await(3, TimeUnit.SECONDS));
            text = dom.get();
            if (text != null && text.contains("Rudy Browser")
                    && text.contains("Google")) break;
            SystemClock.sleep(350);
        }
        assertTrue("Bundled browser home page DOM must contain visible text: " + text,
                text != null && text.contains("Rudy Browser") && text.contains("Google"));

        // The visible screenshot must contain actual homepage foreground pixels,
        // not merely an empty background color that the WebView painted.
        boolean hasBrightText = false;
        for (int attempt = 0; attempt < 8 && !hasBrightText; attempt++) {
            final Bitmap screenshot = instrumentation.getUiAutomation().takeScreenshot();
            assertNotNull("Unable to capture live browser screenshot", screenshot);
            int[] pos = new int[2];
            instrumentation.runOnMainSync(() -> browser.get().getLocationOnScreen(pos));
            int left = Math.max(0, pos[0]);
            int right = Math.min(screenshot.getWidth(), left + browser.get().getWidth());
            int top = Math.max(0, pos[1] + 12);
            int bottom = Math.min(screenshot.getHeight(), top + 550);
            int bright = 0;
            for (int y = top; y < bottom; y += 3) {
                for (int x = left; x < right; x += 3) {
                    int pixel = screenshot.getPixel(x, y);
                    if (Color.red(pixel) > 210 && Color.green(pixel) > 210
                            && Color.blue(pixel) > 210) bright++;
                }
            }
            screenshot.recycle();
            hasBrightText = bright > 40;
            if (!hasBrightText) SystemClock.sleep(700);
        }
        assertTrue("Browser home surface is blank despite fallback HTML being available", hasBrightText);
    }

    @Test public void nativeComposeLandingIsVisibleAndQuickSiteOpensWebView() {
        rule.getScenario().onActivity(activity -> {
            try {
                BrowserHome home = (BrowserHome) field(activity, "composeHome");
                assertNotNull("Preview must attach native Compose landing", home);
                assertNotNull(home.view().getParent());
                assertEquals("Compose landing should be visible on home",
                        View.VISIBLE, home.view().getVisibility());
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        });
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        assertTrue("Native home quick-access UI must render",
                device.wait(Until.hasObject(By.text("Quick access")), 7000));
        assertTrue("Native Compose Google button missing",
                device.wait(Until.hasObject(By.desc("Open Google website")), 5000));
        device.findObject(By.desc("Open Google website")).click();
        assertTrue("Tapping a quick site must hide the native landing asynchronously",
                device.wait(Until.gone(By.text("Quick access")), 10000));
        rule.getScenario().onActivity(activity -> {
            try {
                BrowserHome home = (BrowserHome) field(activity, "composeHome");
                assertEquals("Navigating to an HTTPS site must reveal WebView",
                        View.GONE, home.view().getVisibility());
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        });
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
