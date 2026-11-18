package io.rudycity.tvbrowser;

import android.view.View;

/**
 * Engine-independent toolbar contract. API 19-safe: no Compose references in the legacy app.
 * The optional Kotlin Compose adapter is loaded only in modern Android variants.
 */
public interface BrowserChrome {
    interface Actions {
        void navigate(String input);
        void back();
        void forward();
        void home();
        void reload();
        void bookmark();
        void extensions();
        void menu();
    }

    interface ChoiceHandler {
        /** Called only for an explicitly chosen action; dismiss never invokes callbacks. */
        void onChoice(int index);
    }

    /** Render an accessible Material 3 browser menu; classic/TV shell uses native dialog. */
    void showActions(String title, String[] labels, ChoiceHandler handler);

    View view();
    /** Open omnibox for text input, including from the old advanced tools menu. */
    void focusAddress();

    /**
     * Full URL is for editing only; the Compose toolbar renders a compact host otherwise.
     * Call from the Android UI thread, never directly from the WebView background worker.
     */
    void update(String url, boolean loading, int progress,
                boolean canGoBack, boolean canGoForward, boolean bookmarked);
}
