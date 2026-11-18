package io.rudycity.tvbrowser;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;
import android.view.ViewGroup;
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
import java.util.concurrent.atomic.AtomicInteger;

/** Strictly the device's Android System WebView, NOT a standalone Chromium engine. */
public final class WebViewEngine implements BrowserEngine {
    private final WebView view;
    private final Listener listener;
    private final String defaultUserAgent;
    private final ExtensionSettings extensions;
    private volatile String documentUrl;
    private final AtomicInteger blockedTrackers = new AtomicInteger();
    private volatile boolean destroyed;
    private final Handler popupHandler = new Handler(Looper.getMainLooper());
    private final boolean allowPlainHttp =
            BrowserNetworkPolicy.allowHttpForFlavor(BuildConfig.FLAVOR);
    private WebView pendingPopup;
    private Runnable popupTimeout;

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
        settings.setSupportMultipleWindows(true);
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
                // The old callback cannot distinguish a main-frame navigation from a subresource.
                // Fail open and let the API 21+ overload handle requests safely.
                return null;
            }
            @Override public WebResourceResponse shouldInterceptRequest(
                    WebView webView, WebResourceRequest request) {
                return interceptResource(request.getUrl().toString(), request.isForMainFrame());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView webView, String url) {
                if (BrowserNetworkPolicy.blocksCleartext(url, allowPlainHttp)) {
                    listener.onError("Unencrypted HTTP navigation blocked");
                    return true;
                }
                if (UrlTools.isWebUrl(url)) return false;
                listener.onError("Blocked unsupported link type");
                return true;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView webView, WebResourceRequest request) {
                return shouldOverrideUrlLoading(webView, request.getUrl().toString());
            }
            @Override public void onPageStarted(WebView webView, String url, Bitmap icon) {
                if (BrowserNetworkPolicy.blocksCleartext(url, allowPlainHttp)) {
                    webView.stopLoading();
                    listener.onError("Unencrypted HTTP page blocked");
                    return;
                }
                documentUrl = url;
                blockedTrackers.set(0);
                listener.onPageStarted(url);
            }
            @Override public void onPageFinished(WebView webView, String url) {
                // Redirects and cancelled loads can deliver stale completion callbacks.
                String activeUrl = webView.getUrl();
                if (activeUrl == null || !activeUrl.equals(url)) return;
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
            @Override public boolean onCreateWindow(WebView webView, boolean isDialog,
                                                     boolean isUserGesture, Message resultMsg) {
                // Ads cannot open unattended windows; only one isolated probe is allowed.
                if (destroyed || !isUserGesture || pendingPopup != null
                        || resultMsg == null ||
                        !(resultMsg.obj instanceof WebView.WebViewTransport)) return false;
                final String opener = webView.getUrl();
                final WebView probe = new WebView(webView.getContext());
                WebSettings safe = probe.getSettings();
                safe.setJavaScriptEnabled(false);
                safe.setDomStorageEnabled(false);
                safe.setAllowFileAccess(false);
                safe.setAllowContentAccess(false);
                safe.setAllowFileAccessFromFileURLs(false);
                safe.setAllowUniversalAccessFromFileURLs(false);
                safe.setBlockNetworkLoads(true);
                probe.setWebViewClient(new WebViewClient() {
                    @Override public boolean shouldOverrideUrlLoading(WebView child, String url) {
                        capturePopupDestination(probe, opener, url);
                        return true;
                    }
                    @Override public boolean shouldOverrideUrlLoading(
                            WebView child, WebResourceRequest request) {
                        if (request.isForMainFrame())
                            capturePopupDestination(probe, opener, request.getUrl().toString());
                        return true;
                    }
                    @Override public void onPageStarted(WebView child, String url, Bitmap favicon) {
                        capturePopupDestination(probe, opener, url);
                    }
                    @Override public WebResourceResponse shouldInterceptRequest(WebView child, String url) {
                        return emptyResponse();
                    }
                    @Override public WebResourceResponse shouldInterceptRequest(
                            WebView child, WebResourceRequest request) {
                        return emptyResponse();
                    }
                });
                probe.setWebChromeClient(new WebChromeClient() {
                    @Override public void onCloseWindow(WebView child) {
                        discardPopupProbe(probe);
                    }
                });
                pendingPopup = probe;
                popupTimeout = new Runnable() {
                    @Override public void run() { discardPopupProbe(probe); }
                };
                popupHandler.postDelayed(popupTimeout, 6000);
                try {
                    if (webView.getParent() instanceof ViewGroup) {
                        // Older WebView providers may require an attached popup transport.
                        probe.setVisibility(View.INVISIBLE);
                        ((ViewGroup) webView.getParent()).addView(probe,
                                new ViewGroup.LayoutParams(1, 1));
                    }
                    ((WebView.WebViewTransport) resultMsg.obj).setWebView(probe);
                    resultMsg.sendToTarget();
                    return true;
                } catch (RuntimeException error) {
                    discardPopupProbe(probe);
                    return false;
                }
            }
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

    private static WebResourceResponse emptyResponse() {
        return new WebResourceResponse("text/plain", "UTF-8",
                new ByteArrayInputStream(new byte[0]));
    }

    /** No destination is loaded by the probe, even if the user later approves. */
    private void capturePopupDestination(WebView probe, String opener, String url) {
        if (pendingPopup != probe || destroyed || url == null ||
                "about:blank".equalsIgnoreCase(url)) return;
        discardPopupProbe(probe);
        if (PopupPolicy.isPreviewable(url)) listener.onPopupRequested(opener, url);
        else listener.onError("Unsafe popup destination blocked");
    }

    private void discardPopupProbe(final WebView probe) {
        if (pendingPopup != probe) return;
        pendingPopup = null;
        if (popupTimeout != null) popupHandler.removeCallbacks(popupTimeout);
        popupTimeout = null;
        // Do not destroy a WebView inside its own client callback.
        popupHandler.post(new Runnable() {
            @Override public void run() {
                probe.stopLoading();
                if (probe.getParent() instanceof ViewGroup)
                    ((ViewGroup) probe.getParent()).removeView(probe);
                probe.destroy();
            }
        });
    }

    /** Request interception can run off the UI thread. Use only thread-safe settings. */
    private WebResourceResponse interceptResource(String url, boolean mainFrame) {
        // Main-frame interception is required for early WebView API 21–25.
        if (mainFrame && BrowserNetworkPolicy.blocksCleartext(url, allowPlainHttp)) {
            return emptyResponse();
        }
        String page = documentUrl;
        if (Build.VERSION.SDK_INT >= 21 && !destroyed
                && extensions.enabledForUrl(ExtensionPolicy.AD_BLOCKER, page)
                && ExtensionPolicy.shouldBlockResource(page, url, mainFrame)) {
            blockedTrackers.incrementAndGet();
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

    /** Count of matched tracker subresource requests for the current top-level load. */
    public int blockedTrackerRequests() { return blockedTrackers.get(); }

    /** Executes only allowlisted HTML5 controls on the current web page when enabled. */
    public boolean executeVideoCommand(String command) {
        String javascript = VideoTools.javascript(command);
        String url = view.getUrl();
        if (destroyed || javascript == null || ExtensionPolicy.host(url) == null
                || !extensions.enabledForUrl(ExtensionPolicy.VIDEO_TOOLS, url)) return false;
        if (Build.VERSION.SDK_INT < 19) return false;
        view.evaluateJavascript(javascript, null);
        return true;
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
        if (pendingPopup != null) discardPopupProbe(pendingPopup);
        view.stopLoading();
        if (view.getParent() instanceof android.view.ViewGroup)
            ((android.view.ViewGroup) view.getParent()).removeView(view);
        view.destroy();
    }
}
