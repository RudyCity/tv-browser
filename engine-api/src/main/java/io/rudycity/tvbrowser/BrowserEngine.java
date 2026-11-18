package io.rudycity.tvbrowser;

import android.view.View;

/** A single owned rendering surface; standalone Chromium providers must not use system WebView. */
public interface BrowserEngine {
    interface Listener {
        void onPageStarted(String url);
        void onPageFinished(String url, String title);
        void onProgress(int progress);
        void onError(String message);
        void onFullscreenRequested(View view, Runnable closeAction);
        void onFullscreenEnded();
        void onDownloadRequested(String url, String userAgent, String contentDisposition, String mimeType);
        void onEngineCrashed(String message);
        default void onFullscreenChanged(boolean fullscreen) {}
        /** Called only when a new-window destination has been captured without loading it. */
        void onPopupRequested(String openerUrl, String destinationUrl);
    }

    /** Human-readable, unambiguous engine name. */
    String name();
    View view();
    String currentUrl();
    String title();
    String cookieHeader(String url);
    void loadUrl(String url);
    void loadHomeHtml(String html);
    void reload();
    boolean canGoBack();
    void goBack();
    boolean canGoForward();
    void goForward();
    void zoomIn();
    void zoomOut();
    void setDesktopMode(boolean enabled);
    void executeDpadCommand(String command);
    void clearBrowsingData();
    interface ClearCallback { void onResult(boolean completed); }
    default void clearBrowsingDataWithResult(ClearCallback callback) {
        clearBrowsingData();
        callback.onResult(false);
    }
    default void exitFullscreen() {}
    default boolean showSitePermissions() { return false; }
    default boolean onAndroidPermissionsResult(int requestCode, String[] permissions,
                                               int[] grantResults) { return false; }
    void onResume();
    void onPause();
    void destroy();
}
