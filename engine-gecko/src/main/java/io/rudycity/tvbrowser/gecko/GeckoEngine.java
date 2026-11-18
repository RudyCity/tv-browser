package io.rudycity.tvbrowser.gecko;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.Manifest;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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
    private final GeckoDocumentGateway documents;
    private final Activity activity;
    private static final AtomicInteger NEXT_PERMISSION_ID = new AtomicInteger(5400);
    private AlertDialog sitePermissionDialog;
    private GeckoSession.PermissionDelegate.Callback pendingAndroidPermission;
    private int pendingAndroidRequestCode = -1;
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
    private final boolean privateMode;

    public GeckoEngine(Activity activity, Listener callback, boolean desktop) {
        this(activity, callback, desktop, false);
    }

    public GeckoEngine(Activity activity, Listener callback, boolean desktop,
                       boolean privateMode) {
        this.privateMode = privateMode;
        this.activity = activity;
        listener = callback;
        runtime = GeckoRuntime.getDefault(activity.getApplicationContext());
        extensions = new GeckoExtensionManager(activity, runtime);
        session = new GeckoSession(new GeckoSessionSettings.Builder()
                .usePrivateMode(privateMode).useTrackingProtection(true).build());
        view = new GeckoView(activity);
        documents = new GeckoDocumentGateway(activity, session, view);
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
                documents.setPageUrl(url);
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
                documents.setPageUrl(url);
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
            @Override public void onFullScreen(GeckoSession from, boolean fullScreen) {
                if (!destroyed) listener.onFullscreenChanged(fullScreen);
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
        // Every site permission remains denied unless a visible user explicitly
        // approves the origin; Android runtime grants are a separate gate.
        session.setPromptDelegate(documents);
        session.setPermissionDelegate(new GeckoSession.PermissionDelegate() {
            @Override public void onAndroidPermissionsRequest(
                    GeckoSession from, String[] requested, Callback callback) {
                requestAndroidPermissions(requested, callback);
            }
            @Override public void onMediaPermissionRequest(
                    GeckoSession from, String origin, MediaSource[] video,
                    MediaSource[] audio, MediaCallback callback) {
                if (!promptAllowed(origin) ||
                        (video != null && video.length > 0 && !hasAndroidPermission(Manifest.permission.CAMERA)) ||
                        (audio != null && audio.length > 0 && !hasAndroidPermission(Manifest.permission.RECORD_AUDIO))) {
                    callback.reject();
                    return;
                }
                String devices = (video != null && video.length > 0 ? "Camera " : "") +
                        (audio != null && audio.length > 0 ? "Microphone" : "");
                promptSite("Allow site media capture?",
                        safeOrigin(origin) + "\n\nRequested: " + devices +
                        "\n\nOnly approve sites you trust.",
                        null, approved -> {
                            if (approved && !destroyed)
                                callback.grant(video != null && video.length > 0 ? video[0] : null,
                                        audio != null && audio.length > 0 ? audio[0] : null);
                            else callback.reject();
                        });
            }
            @Override public GeckoResult<Integer> onContentPermissionRequest(
                    GeckoSession from, ContentPermission permission) {
                return requestSitePermission(permission);
            }
        });
        setDesktopMode(desktop);
        session.open(runtime);
        view.setSession(session);
    }

    private interface Decision { void complete(boolean approved); }

    private boolean hasAndroidPermission(String name) {
        return activity.checkSelfPermission(name) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean promptAllowed(String origin) {
        return !destroyed && view.getVisibility() == View.VISIBLE &&
                !activity.isFinishing() && !activity.isDestroyed() &&
                isHttps(origin);
    }

    private String safeOrigin(String uri) {
        try {
            Uri parsed = Uri.parse(uri);
            return parsed.getScheme() + "://" + parsed.getHost() +
                    (parsed.getPort() < 0 ? "" : ":" + parsed.getPort());
        } catch (RuntimeException ignored) {
            return "Unknown site";
        }
    }

    private void promptSite(String title, String message, Runnable onShown,
                            Decision decision) {
        if (destroyed || sitePermissionDialog != null ||
                activity.isFinishing() || activity.isDestroyed() ||
                view.getVisibility() != View.VISIBLE) {
            decision.complete(false);
            return;
        }
        final AtomicBoolean settled = new AtomicBoolean(false);
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(title).setMessage(message)
                .setNegativeButton("Block", (ignored, which) -> {
                    if (settled.compareAndSet(false, true)) decision.complete(false);
                })
                .setPositiveButton("Allow", (ignored, which) -> {
                    if (settled.compareAndSet(false, true)) decision.complete(true);
                })
                .create();
        sitePermissionDialog = dialog;
        dialog.setOnDismissListener(ignored -> {
            if (sitePermissionDialog == dialog) sitePermissionDialog = null;
            if (settled.compareAndSet(false, true)) decision.complete(false);
        });
        dialog.show();
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).requestFocus();
        if (onShown != null) onShown.run();
    }

    private static String contentPermissionName(int permission) {
        switch (permission) {
            case GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION: return "Location";
            case GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION: return "Notifications";
            case GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE: return "Autoplay with sound";
            case GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE: return "Silent autoplay";
            case GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS: return "Protected media / DRM";
            case GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE: return "Persistent storage";
            case GeckoSession.PermissionDelegate.PERMISSION_STORAGE_ACCESS: return "Third-party cookie access";
            case GeckoSession.PermissionDelegate.PERMISSION_TRACKING: return "Disable tracking protection";
            default: return "Restricted permission (" + permission + ")";
        }
    }

    private GeckoResult<Integer> requestSitePermission(
            GeckoSession.PermissionDelegate.ContentPermission permission) {
        final int type = permission.permission;
        // Known, user-comprehensible permissions only. Persistent storage is
        // irreversible per Mozilla; trackers, XR and third-party cookies stay blocked.
        boolean supported = type == GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION ||
                type == GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE ||
                type == GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE ||
                type == GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS;
        if (!supported || !promptAllowed(permission.uri)) return GeckoResult.fromValue(
                GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY);
        if (type == GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION &&
                !hasAndroidPermission(Manifest.permission.ACCESS_FINE_LOCATION) &&
                !hasAndroidPermission(Manifest.permission.ACCESS_COARSE_LOCATION))
            return GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY);
        final GeckoResult<Integer> outcome = new GeckoResult<>();
        promptSite("Website permission: " + contentPermissionName(type),
                safeOrigin(permission.uri) + "\n\nAllow " + contentPermissionName(type) +
                " for this site? You can reset it from Browser tools → Site permissions.",
                permission::notifyShown,
                approved -> outcome.complete(approved
                        ? GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
                        : GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY));
        return outcome;
    }

    private void requestAndroidPermissions(String[] requested,
            GeckoSession.PermissionDelegate.Callback callback) {
        if (requested == null || requested.length == 0 ||
                !promptAllowed(location) || pendingAndroidPermission != null) {
            callback.reject(); return;
        }
        List<String> missing = new ArrayList<>();
        for (String item : requested) {
            if (!Manifest.permission.CAMERA.equals(item) &&
                    !Manifest.permission.RECORD_AUDIO.equals(item) &&
                    !Manifest.permission.ACCESS_FINE_LOCATION.equals(item) &&
                    !Manifest.permission.ACCESS_COARSE_LOCATION.equals(item)) {
                callback.reject(); return;
            }
            if (!hasAndroidPermission(item)) missing.add(item);
        }
        if (missing.isEmpty()) { callback.grant(); return; }
        promptSite("Android device permission",
                safeOrigin(location) + "\n\nSite requests Android permissions:\n" +
                Arrays.toString(missing.toArray()) +
                "\n\nThe Android system will ask again. Nothing is granted automatically.",
                null, approved -> {
                    if (!approved || destroyed) { callback.reject(); return; }
                    int requestCode = NEXT_PERMISSION_ID.getAndIncrement();
                    pendingAndroidPermission = callback;
                    pendingAndroidRequestCode = requestCode;
                    try {
                        activity.requestPermissions(missing.toArray(new String[0]), requestCode);
                    } catch (RuntimeException error) {
                        pendingAndroidPermission = null;
                        pendingAndroidRequestCode = -1;
                        callback.reject();
                    }
                });
    }

    @Override public boolean onAndroidPermissionsResult(int code, String[] permissions,
                                                           int[] grants) {
        if (code != pendingAndroidRequestCode || pendingAndroidPermission == null)
            return false;
        GeckoSession.PermissionDelegate.Callback callback = pendingAndroidPermission;
        pendingAndroidPermission = null;
        pendingAndroidRequestCode = -1;
        boolean allGranted = !destroyed && grants.length == permissions.length && grants.length > 0;
        for (int result : grants) allGranted &= result == PackageManager.PERMISSION_GRANTED;
        if (allGranted) callback.grant();
        else callback.reject();
        return true;
    }

    @Override public boolean savePagePdf() { return documents.savePagePdf(); }
    @Override public boolean onActivityResult(int code, int result, android.content.Intent data) {
        return documents.onActivityResult(code, result, data);
    }
    @Override public boolean showSitePermissions() {
        if (destroyed || !isHttps(location)) return false;
        String selectedOrigin = location;
        runtime.getStorageController().getPermissions(selectedOrigin, privateMode)
                .accept(perms -> activity.runOnUiThread(() -> {
                    if (destroyed || activity.isFinishing() || activity.isDestroyed()) return;
                    if (perms == null || perms.isEmpty()) {
                        new AlertDialog.Builder(activity).setTitle("Site permissions")
                                .setMessage(safeOrigin(selectedOrigin) +
                                        "\n\nNo stored website permissions.")
                                .setPositiveButton("OK", null).show();
                        return;
                    }
                    String[] labels = new String[perms.size()];
                    for (int i = 0; i < perms.size(); i++) {
                        GeckoSession.PermissionDelegate.ContentPermission p = perms.get(i);
                        labels[i] = contentPermissionName(p.permission) + " — " +
                                (p.value == GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
                                        ? "Allowed" : p.value ==
                                        GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
                                        ? "Blocked" : "Ask");
                    }
                    new AlertDialog.Builder(activity).setTitle("Site permissions: " +
                            safeOrigin(selectedOrigin)).setItems(labels, (dialog, index) -> {
                        GeckoSession.PermissionDelegate.ContentPermission selected = perms.get(index);
                        if (selected.permission == GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE) {
                            new AlertDialog.Builder(activity).setTitle("Persistent storage")
                                    .setMessage("Mozilla does not support revoking an existing " +
                                            "persistent-storage grant. Use Clear browsing data.")
                                    .setPositiveButton("OK", null).show();
                            return;
                        }
                        new AlertDialog.Builder(activity).setTitle("Reset site permission?")
                                .setMessage(contentPermissionName(selected.permission) + "\n" +
                                        safeOrigin(selected.uri))
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Reset to Ask", (ignored, which) ->
                                        runtime.getStorageController().setPermission(selected,
                                                GeckoSession.PermissionDelegate.ContentPermission.VALUE_PROMPT))
                                .show();
                    }).setNegativeButton("Close", null).show();
                }), error -> activity.runOnUiThread(() -> {
                    if (!destroyed && !activity.isFinishing())
                        new AlertDialog.Builder(activity).setTitle("Site permissions unavailable")
                                .setMessage("Firefox could not read this site's permissions.")
                                .setPositiveButton("OK", null).show();
                }));
        return true;
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
    public boolean isPrivateSession() { return privateMode; }
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
        clearBrowsingDataWithResult(completed -> {
            if (!completed && !destroyed) listener.onError("Firefox data clearing failed");
        });
    }
    @Override public void clearBrowsingDataWithResult(ClearCallback callback) {
        if (destroyed) { callback.onResult(false); return; }
        // Gecko storage may re-accumulate data if its sessions remain open.
        session.purgeHistory();
        destroy();
        runtime.getStorageController().clearData(StorageController.ClearFlags.ALL)
                .then(ignored -> {
                    callback.onResult(true);
                    return null;
                }, error -> {
                    callback.onResult(false);
                    return null;
                });
    }
    @Override public void exitFullscreen() {
        if (!destroyed) session.exitFullScreen();
    }
    @Override public void onResume() { if (!destroyed) session.setActive(true); }
    @Override public void onPause() { if (!destroyed) session.setActive(false); }
    @Override public void destroy() {
        if (destroyed) return;
        destroyed = true;
        documents.destroy();
        if (sitePermissionDialog != null) sitePermissionDialog.dismiss();
        sitePermissionDialog = null;
        if (pendingAndroidPermission != null) pendingAndroidPermission.reject();
        pendingAndroidPermission = null;
        pendingAndroidRequestCode = -1;
        extensions.destroy();
        view.releaseSession();
        session.close();
        if (view.getParent() instanceof ViewGroup)
            ((ViewGroup) view.getParent()).removeView(view);
        // The process-wide default runtime is reused by future sessions.
    }
}
