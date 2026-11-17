package io.rudycity.tvbrowser;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.Build;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;

/** Strictly the device's Android System WebView, NOT a standalone Chromium engine. */
public final class WebViewEngine implements BrowserEngine {
    private final WebView view;
    private final Listener listener;
    private final String defaultUserAgent;
    private final ExtensionSettings extensions;
    private volatile String documentUrl;
    private boolean destroyed;

    public WebViewEngine(Activity activity, Listener callback, boolean desktop) {
        listener = callback;
        extensions = new ExtensionSettings(activity.getSharedPreferences("tv_browser", Context.MODE_PRIVATE));
        view = new WebView(activity);
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        view.setWebContentsDebuggingEnabled(false);
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        if (Build.VERSION.SDK_INT >= 21) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }
        if (Build.VERSION.SDK_INT >= 26) settings.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= 21) CookieManager.getInstance().setAcceptThirdPartyCookies(view, false);
        defaultUserAgent = settings.getUserAgentString();
        setDesktopMode(desktop);

        view.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView webView, String url) {
                return interceptResource(url, false);
            }
            @Override public WebResourceResponse shouldInterceptRequest(
                    WebView webView, WebResourceRequest request) {
                return interceptResource(request.getUrl().toString(), request.isForMainFrame());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView webView, String url) {
                if (UrlTools.isWebUrl(url)) return false;
                listener.onError("Blocked unsupported link type");
                return true;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView webView, WebResourceRequest request) {
                return shouldOverrideUrlLoading(webView, request.getUrl().toString());
            }
            @Override public void onPageStarted(WebView webView, String url, Bitmap icon) {
                documentUrl = url;
                listener.onPageStarted(url);
            }
            @Override public void onPageFinished(WebView webView, String url) {
                documentUrl = url;
                injectPageExtensions(webView, url);
                listener.onPageFinished(url, webView.getTitle());
            }
            @Override public void onReceivedSslError(WebView webView, SslErrorHandler handler, SslError error) {
                handler.cancel();
                listener.onError("Invalid HTTPS certificate; connection blocked");
            }
            @Override public void onReceivedError(WebView webView, int errorCode,
                                                   String description, String failingUrl) {
                if (failingUrl != null && failingUrl.equals(webView.getUrl()))
                    listener.onError("Page failed to load: " + description);
            }
            @Override public boolean onRenderProcessGone(WebView webView, android.webkit.RenderProcessGoneDetail detail) {
                listener.onEngineCrashed("WebView renderer exited");
                return true;
            }
        });
        view.setWebChromeClient(new WebChromeClient() {
            @Override public void onShowCustomView(View fullscreen, final CustomViewCallback callback) {
                listener.onFullscreenRequested(fullscreen, new Runnable() {
                    @Override public void run() { callback.onCustomViewHidden(); }
                });
            }
            @Override public void onHideCustomView() { listener.onFullscreenEnded(); }
            @Override public void onProgressChanged(WebView webView, int progress) {
                listener.onProgress(progress);
            }
        });
        view.setDownloadListener(new DownloadListener() {
            @Override public void onDownloadStart(String url, String userAgent,
                                                   String disposition, String mimeType, long size) {
                listener.onDownloadRequested(url, userAgent, disposition, mimeType);
            }
        });
    }

    /** Request interception can run off the UI thread. Use only thread-safe settings. */
    private WebResourceResponse interceptResource(String url, boolean mainFrame) {
        String page = documentUrl;
        if (extensions.enabledForUrl(ExtensionPolicy.AD_BLOCKER, page)
                && ExtensionPolicy.shouldBlockResource(page, url, mainFrame)) {
            return new WebResourceResponse("text/plain", "UTF-8",
                    new ByteArrayInputStream(new byte[0]));
        }
        return null;
    }

    private void injectPageExtensions(WebView webView, String url) {
        if (destroyed || Build.VERSION.SDK_INT < 19) return;
        if (ExtensionPolicy.host(url) != null
                && extensions.enabledForUrl(ExtensionPolicy.DARK_MODE, url))
            webView.evaluateJavascript(ExtensionScripts.DARK_MODE, null);
        if (extensions.enabledForUrl(ExtensionPolicy.TV_NAVIGATION, url))
            webView.evaluateJavascript(TvNavigation.INSTALL, null);
        String script = extensions.scriptForUrl(url);
        if (!script.isEmpty()) {
            // Local opt-in source at an exact HTTPS hostname, never fetched remotely.
            webView.evaluateJavascript("(function(){try{\n" + script
                    + "\n}catch(__tvBrowserScriptError){}})();", null);
        }
    }

    @Override public String name() { return "System WebView"; }
    @Override public View view() { return view; }
    @Override public String currentUrl() { return view.getUrl(); }
    @Override public String title() { return view.getTitle(); }
    @Override public String cookieHeader(String url) { return CookieManager.getInstance().getCookie(url); }
    @Override public void loadUrl(String url) { view.loadUrl(url); }
    @Override public void loadHomeHtml(String html) { view.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null); }
    @Override public void reload() { view.reload(); }
    @Override public boolean canGoBack() { return view.canGoBack(); }
    @Override public void goBack() { view.goBack(); }
    @Override public boolean canGoForward() { return view.canGoForward(); }
    @Override public void goForward() { view.goForward(); }
    @Override public void zoomIn() { view.zoomIn(); }
    @Override public void zoomOut() { view.zoomOut(); }
    @Override public void setDesktopMode(boolean enabled) {
        view.getSettings().setUserAgentString(enabled
                ? "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Safari/537.36"
                : defaultUserAgent);
    }
    @Override public void executeDpadCommand(String command) {
        // Commands are an internal enum-like constant chosen by the Activity, never web input.
        if (command.matches("move\\(-?1,-?1\\)|move\\(-?1,0\\)|move\\(0,-?1\\)|select\\(\\)")) {
            view.evaluateJavascript("if(window.__tvNav)window.__tvNav." + command + ";", null);
        }
    }
    @Override public void clearBrowsingData() {
        view.clearCache(true);
        view.clearHistory();
        view.clearFormData();
        WebStorage.getInstance().deleteAllData();
        if (Build.VERSION.SDK_INT >= 21) CookieManager.getInstance().removeAllCookies(null);
        else CookieManager.getInstance().removeAllCookie();
    }
    @Override public void onResume() { view.onResume(); }
    @Override public void onPause() { view.onPause(); }
    @Override public void destroy() {
        if (destroyed) return;
        destroyed = true;
        view.stopLoading();
        if (view.getParent() instanceof android.view.ViewGroup)
            ((android.view.ViewGroup) view.getParent()).removeView(view);
        view.destroy();
    }
}
