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
    void onResume();
    void onPause();
    void destroy();
}
