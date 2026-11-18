package io.rudycity.tvbrowser.gecko;

import android.app.Activity;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

import io.rudycity.tvbrowser.BrowserEngine;
import io.rudycity.tvbrowser.BrowserExtensions;
import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.StorageController;
import org.mozilla.geckoview.WebResponse;

/**
 * Real Mozilla engine. Installed ONLY with the API26+ gecko flavor.
 * Runtime, storage and cookies are not shared with Android System WebView.
 */
public final class GeckoEngine implements BrowserEngine, BrowserExtensions {
    private final GeckoRuntime runtime;
    private final GeckoExtensionManager extensions;
    private final GeckoSession session;
    private final GeckoView view;
    private final Listener listener;
    private String location;
    private String pageTitle;
    private boolean back;
    private boolean forward;
    private boolean firstContentfulPaint;
    private boolean destroyed;

    public GeckoEngine(Activity activity, Listener callback, boolean desktop) {
        listener = callback;
        runtime = GeckoRuntime.getDefault(activity.getApplicationContext());
        extensions = new GeckoExtensionManager(activity, runtime);
        session = new GeckoSession();
        view = new GeckoView(activity);
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);

        session.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override public GeckoResult<AllowOrDeny> onLoadRequest(
                    GeckoSession from, LoadRequest request) {
                if (destroyed) return GeckoResult.fromValue(AllowOrDeny.DENY);
                // Do not ever hand new-window content a fresh unattended GeckoSession.
                if (request.target == TARGET_WINDOW_NEW) {
                    if (request.hasUserGesture && isHttps(request.uri)) {
                        listener.onPopupRequested(location, request.uri);
                    }
                    return GeckoResult.fromValue(AllowOrDeny.DENY);
                }
                if (isHttps(request.uri)) return null; // Gecko's own HTTPS/TLS checks remain active.
                // Only our internally-created HTML start page may use a data: document.
                if (request.isDirectNavigation && request.uri.startsWith("data:text/html")) {
                    return null;
                }
                listener.onError("Blocked insecure or unsupported navigation");
                return GeckoResult.fromValue(AllowOrDeny.DENY);
            }
            @Override public void onCanGoBack(GeckoSession from, boolean allowed) {
                back = allowed;
            }
            @Override public void onCanGoForward(GeckoSession from, boolean allowed) {
                forward = allowed;
            }
            @Override public void onLocationChange(
                    GeckoSession from, String url,
                    List<GeckoSession.PermissionDelegate.ContentPermission> perms,
                    Boolean hasUserGesture) {
                location = url;
            }
            @Override public GeckoResult<GeckoSession> onNewSession(
                    GeckoSession from, String uri) {
                // No session is created until a real tab manager with explicit approval exists.
                return null;
            }
        });
        session.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override public void onPageStart(GeckoSession from, String url) {
                location = url;
                firstContentfulPaint = false;
                listener.onPageStarted(url);
            }
            @Override public void onProgressChange(GeckoSession from, int progress) {
                listener.onProgress(progress);
            }
            @Override public void onPageStop(GeckoSession from, boolean success) {
                listener.onPageFinished(location, pageTitle);
                if (!success) listener.onError("Firefox engine could not finish loading this page");
            }
        });
        session.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override public void onTitleChange(GeckoSession from, String title) {
                pageTitle = title;
            }
            @Override public void onFirstContentfulPaint(GeckoSession from) {
                firstContentfulPaint = true;
            }
            @Override public void onCrash(GeckoSession from) {
                if (!destroyed) listener.onEngineCrashed("Gecko content process crashed");
            }
            @Override public void onKill(GeckoSession from) {
                if (!destroyed) listener.onEngineCrashed("Gecko content process was killed");
            }
            @Override public void onExternalResponse(GeckoSession from, WebResponse response) {
                // Existing Activity requires human approval before DownloadManager is used.
                if (!destroyed && response != null && isHttps(response.uri))
                    listener.onDownloadRequested(response.uri, null, null, null);
            }
        });
        setDesktopMode(desktop);
        session.open(runtime);
        view.setSession(session);
    }

    private static boolean isHttps(String url) {
        if (url == null) return false;
        try {
            Uri uri = Uri.parse(url);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null && !uri.getHost().isEmpty();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /** Runtime tests must see a real first contentful paint, not merely a constructed View. */
    public boolean hasFirstContentfulPaint() { return firstContentfulPaint; }
    @Override public void listAddons(ListCallback callback) {
        extensions.listAddons(callback);
    }
    @Override public void installBundledTestAddon(ActionCallback callback) {
        extensions.installBundledTestAddon(callback);
    }
    @Override public void installSignedXpi(String httpsUrl, ActionCallback callback) {
        extensions.installSignedXpi(httpsUrl, callback);
    }
    @Override public void setAddonEnabled(String id, boolean enabled, ActionCallback callback) {
        extensions.setAddonEnabled(id, enabled, callback);
    }
    @Override public void uninstallAddon(String id, ActionCallback callback) {
        extensions.uninstallAddon(id, callback);
    }
    @Override public String name() { return "Firefox GeckoView"; }
    @Override public View view() { return view; }
    @Override public String currentUrl() { return location; }
    @Override public String title() { return pageTitle; }
    @Override public String cookieHeader(String url) {
        // Gecko cookies must never be obtained through Android WebView CookieManager.
        return null;
    }
    @Override public void loadUrl(String url) {
        if (!destroyed && isHttps(url)) session.loadUri(url);
        else listener.onError("Only HTTPS navigation is supported in GeckoView preview");
    }
    @Override public void loadHomeHtml(String html) {
        if (!destroyed) session.load(new GeckoSession.Loader().data(html, "text/html"));
    }
    @Override public void reload() { if (!destroyed) session.reload(); }
    @Override public boolean canGoBack() { return back; }
    @Override public void goBack() { if (!destroyed && back) session.goBack(); }
    @Override public boolean canGoForward() { return forward; }
    @Override public void goForward() { if (!destroyed && forward) session.goForward(); }
    @Override public void zoomIn() {
        listener.onError("Gecko page zoom controls are not yet implemented");
    }
    @Override public void zoomOut() {
        listener.onError("Gecko page zoom controls are not yet implemented");
    }
    @Override public void setDesktopMode(boolean desktop) {
        session.getSettings().setUserAgentMode(desktop
                ? GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                : GeckoSessionSettings.USER_AGENT_MODE_MOBILE);
    }
    @Override public void executeDpadCommand(String command) {
        // GeckoView handles native key events itself; we do not inject JavaScript into pages.
    }
    @Override public void clearBrowsingData() {
        if (destroyed) return;
        session.purgeHistory();
        runtime.getStorageController().clearData(StorageController.ClearFlags.ALL)
                .then(ignored -> null, error -> {
                    listener.onError("Firefox data clearing failed");
                    return null;
                });
    }
    @Override public void onResume() { if (!destroyed) session.setActive(true); }
    @Override public void onPause() { if (!destroyed) session.setActive(false); }
    @Override public void destroy() {
        if (destroyed) return;
        destroyed = true;
        extensions.destroy();
        view.releaseSession();
        session.close();
        if (view.getParent() instanceof ViewGroup)
            ((ViewGroup) view.getParent()).removeView(view);
        // The process-wide default runtime is reused by future sessions.
    }
}
