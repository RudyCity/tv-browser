package io.rudycity.tvbrowser;

import android.view.View;

/**
 * Optional landing page presentation, independent of browser engine/session.
 * API19-safe contract; legacy Android and TV continue to show a WebView home.
 */
public interface BrowserHome {
    interface Actions {
        void openWebsite(String httpsUrl);
        void focusAddress();
    }

    View view();
    /** Optional native landing-page theme; legacy engines ignore this. */
    default void setDarkTheme(boolean dark) {}
}
