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

    public static Selection create(Activity activity, String preference,
                                   boolean desktop, BrowserEngine.Listener listener) {
        String safePreference = EnginePolicy.sanitize(preference);
        boolean present = false;
        Class<?> provider = null;
        if (Build.VERSION.SDK_INT >= EnginePolicy.MIN_MODERN_CHROMIUM_API &&
                !EnginePolicy.WEBVIEW.equals(safePreference)) {
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
