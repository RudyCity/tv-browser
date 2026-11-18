package io.rudycity.tvbrowser;

import android.app.Activity;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/** Loads an optionally packaged, first-party Chromium embedder; fails closed to system WebView. */
public final class EngineFactory {
    private static final String TAG = "TvBrowserEngine";
    // Implemented by a separately built Chromium content embedder, NEVER a system WebView wrapper.
    private static final String GECKO_PROVIDER = "io.rudycity.tvbrowser.gecko.GeckoEngine";
    private static final String PROVIDER = "io.rudycity.tvbrowser.chromium.EmbeddedChromiumProvider";

    private EngineFactory() {}

    public static final class Selection {
        public final BrowserEngine engine;
        public final String reason;
        public final boolean fallback;
        Selection(BrowserEngine engine, String reason, boolean fallback) {
            this.engine = engine;
            this.reason = reason;
            this.fallback = fallback;
        }
    }

    /** Private tabs must never fall back to a shared System WebView cookie jar. */
    public static Selection createPrivate(Activity activity, boolean desktop,
                                          BrowserEngine.Listener listener) {
        if (Build.VERSION.SDK_INT < EnginePolicy.MIN_GECKO_API)
            throw new IllegalStateException("Private GeckoView needs Android 8+");
        try {
            Class<?> gecko = Class.forName(GECKO_PROVIDER, false, activity.getClassLoader());
            Constructor<?> constructor = gecko.getConstructor(
                    Activity.class, BrowserEngine.Listener.class, boolean.class, boolean.class);
            BrowserEngine engine = (BrowserEngine) constructor.newInstance(
                    activity, listener, desktop, true);
            if (engine == null || !"Firefox GeckoView".equals(engine.name()) ||
                    engine.view() == null || engine.view() instanceof android.webkit.WebView)
                throw new IllegalStateException("Private Gecko renderer invalid");
            return new Selection(engine, "Private GeckoView session", false);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
            // Deliberately no WebView fallback: it would share cookies with normal tabs.
            throw new IllegalStateException("Private GeckoView unavailable", unavailable);
        }
    }

    public static Selection create(Activity activity, String preference,
                                   boolean desktop, BrowserEngine.Listener listener) {
        String safePreference = EnginePolicy.sanitize(preference);
        if (Build.VERSION.SDK_INT >= EnginePolicy.MIN_GECKO_API &&
                (EnginePolicy.AUTO.equals(safePreference) || EnginePolicy.GECKO.equals(safePreference))) {
            try {
                Class<?> gecko = Class.forName(GECKO_PROVIDER, false, activity.getClassLoader());
                Constructor<?> constructor = gecko.getConstructor(
                        Activity.class, BrowserEngine.Listener.class, boolean.class);
                BrowserEngine engine = (BrowserEngine) constructor.newInstance(
                        activity, listener, desktop);
                if (engine == null || engine.view() == null ||
                        !"Firefox GeckoView".equals(engine.name()) ||
                        engine.view() instanceof android.webkit.WebView)
                    throw new IllegalStateException("Gecko provider is not independent");
                return new Selection(engine, "Firefox GeckoView active", false);
            } catch (ClassNotFoundException unavailable) {
                // Gecko binary deliberately absent from legacy and Preview APKs.
            } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
                Log.e(TAG, "Gecko provider failed; using System WebView", unavailable);
                return new Selection(new WebViewEngine(activity, listener, desktop),
                        "GeckoView failed to start; separate System WebView fallback active", true);
            }
        }
        boolean present = false;
        Class<?> provider = null;
        if (Build.VERSION.SDK_INT >= EnginePolicy.MIN_MODERN_CHROMIUM_API &&
                !EnginePolicy.WEBVIEW.equals(safePreference) &&
                !EnginePolicy.GECKO.equals(safePreference)) {
            try {
                provider = Class.forName(PROVIDER, false, activity.getClassLoader());
                present = true;
            } catch (ClassNotFoundException unavailable) {
                // Expected for the legacy-only APK. Never mislabel WebView as Chromium.
            } catch (LinkageError | RuntimeException unavailable) {
                Log.w(TAG, "Embedded provider cannot be loaded", unavailable);
            }
        }
        if (EnginePolicy.tryChromium(Build.VERSION.SDK_INT, safePreference, present)) {
            try {
                Constructor<?> constructor = provider.getConstructor(
                        Activity.class, BrowserEngine.Listener.class, boolean.class);
                BrowserEngine engine = (BrowserEngine) constructor.newInstance(activity, listener, desktop);
                if (engine == null || engine.view() == null) throw new IllegalStateException("No Chromium surface");
                if (!"Embedded Chromium".equals(engine.name()) ||
                        engine.view() instanceof android.webkit.WebView)
                    throw new IllegalStateException("Provider is not an independent Chromium renderer");
                return new Selection(engine, "Embedded Chromium active", false);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
                // Do not expose exception text (may contain URLs); keep a safe reason.
                Log.e(TAG, "Embedded Chromium initialization failed", error);
                return new Selection(new WebViewEngine(activity, listener, desktop),
                        "Embedded Chromium failed to start; WebView fallback active", true);
            }
        }
        return new Selection(new WebViewEngine(activity, listener, desktop),
                EnginePolicy.fallbackReason(Build.VERSION.SDK_INT, safePreference, present),
                !EnginePolicy.WEBVIEW.equals(safePreference));
    }
}
