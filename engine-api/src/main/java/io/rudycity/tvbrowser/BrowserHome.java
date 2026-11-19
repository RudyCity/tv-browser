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
        default void openTabs() {}
        default void openBookmarks() {}
        default void openHistory() {}
        default void openSettings() {}
        default void openTools() {}
        default void openExtensions() {}
        default void newTab() {}
        default void newPrivateTab() {}
    }

    View view();
    /** Optional native landing-page theme; legacy engines ignore this. */
    default void setDarkTheme(boolean dark) {}
    /** Refreshes active home data (history, bookmarks, tabs, speed dial) upon visibility change. */
    default void refreshData() {}
}
