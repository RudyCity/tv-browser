package io.rudycity.tvbrowser;

import android.view.View;
import android.widget.ProgressBar;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
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
