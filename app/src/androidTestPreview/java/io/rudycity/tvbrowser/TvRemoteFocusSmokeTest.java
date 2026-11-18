package io.rudycity.tvbrowser;

import android.content.Context;
import android.content.res.Configuration;
import android.view.KeyEvent;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import static org.junit.Assert.*;

/** Must be executed on an Android TV system image, never a Pixel phone fake. */
@RunWith(AndroidJUnit4.class)
public final class TvRemoteFocusSmokeTest {
    /**
     * Android phone API29 runs the whole Preview suite. Only Android TV images
     * expose Leanback; skip TV-only assertions on phones but keep full Android
     * TV coverage in the dedicated android-tv.yml emulator job.
     */
    @Before public void requireRealAndroidTv() {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Assume.assumeTrue("Android TV-only instrumentation; use android-tv.yml",
                target.getPackageManager().hasSystemFeature("android.software.leanback"));
    }

    private static boolean boolField(MainActivity activity, String name) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Boolean) field.get(activity);
    }

    @Test public void realTvEnvironmentActivatesTenFootControls() {
        try (ActivityScenario<MainActivity> scenario =
                     ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    assertTrue("TV system image must advertise Leanback",
                            activity.getPackageManager().hasSystemFeature(
                                    "android.software.leanback"));
                    int type = activity.getResources().getConfiguration().uiMode &
                            Configuration.UI_MODE_TYPE_MASK;
                    assertTrue("Must detect Android TV rather than phone UI",
                            boolField(activity, "tvDevice"));
                    assertTrue("TV layout policy", BrowserLayoutPolicy.isTelevision(
                            type == Configuration.UI_MODE_TYPE_TELEVISION,
                            true, false, false));
                    BrowserEngine renderer = (BrowserEngine) getField(activity, "engine");
                    assertNotNull(renderer);
                    assertNotNull(renderer.view());
                } catch (Exception error) {
                    throw new AssertionError("Android TV runtime verification", error);
                }
            });
        }
    }

    @Test public void tvMenuAndRemoteInputDoNotKillRenderer() {
        try (ActivityScenario<MainActivity> scenario =
                     ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    BrowserEngine original = (BrowserEngine) getField(activity, "engine");
                    assertTrue(boolField(activity, "tvDevice"));
                    assertTrue("Menu key must open browser UI", activity.dispatchKeyEvent(
                            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU)));
                    // Hardware-driven page-up/down must preserve live renderer ownership.
                    activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,
                            KeyEvent.KEYCODE_PAGE_DOWN));
                    assertSame(original, getField(activity, "engine"));
                    assertNotNull(original.view().getParent());
                } catch (Exception error) {
                    throw new AssertionError("TV D-pad/browser menu regression", error);
                }
            });
        }
    }

    private static Object getField(Object object, String name) throws Exception {
        Field f = object.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(object);
    }
}
