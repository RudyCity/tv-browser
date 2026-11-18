package io.rudycity.tvbrowser.gecko;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.rudycity.tvbrowser.BrowserExtensions;
import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.WebExtension;
import org.mozilla.geckoview.WebExtensionController;

/**
 * Gecko's real installed WebExtension store, permission prompts, and signed XPI loader.
 * No CRX/Chrome Web Store support, no automatic remote extension downloads.
 */
final class GeckoExtensionManager implements BrowserExtensions {
    private static final String BUILTIN_URI =
            "resource://android/assets/rudy_page_marker/";
    private static final String BUILTIN_ID = "rudy-page-marker@rudycity.browser";
    private final Activity activity;
    private final WebExtensionController controller;
    private boolean destroyed;
    private AlertDialog pendingPrompt;

    GeckoExtensionManager(Activity activity, GeckoRuntime runtime) {
        this.activity = activity;
        controller = runtime.getWebExtensionController();
        // Every remotely installed XPI's required permissions, origin and data-collection
        // declarations need actual human consent. Denial is always the default.
        controller.setPromptDelegate(new WebExtensionController.PromptDelegate() {
            @Override public GeckoResult<WebExtension.PermissionPromptResponse> onInstallPromptRequest(
                    WebExtension extension, String[] permissions, String[] origins,
                    String[] dataCollectionPermissions) {
                final GeckoResult<WebExtension.PermissionPromptResponse> result =
                        new GeckoResult<>();
                promptUser("Install Firefox extension?",
                        extensionTitle(extension) + describePermissions(
                                permissions, origins, dataCollectionPermissions)
                                + "\n\nMozilla validates the XPI signature. Allow this extension?",
                        approved -> result.complete(new WebExtension.PermissionPromptResponse(
                                approved, false, false)));
                return result;
            }

            @Override public GeckoResult<AllowOrDeny> onOptionalPrompt(
                    WebExtension extension, String[] permissions, String[] origins,
                    String[] dataCollectionPermissions) {
                return promptPermission("Grant additional Firefox permissions?",
                        extension, permissions, origins, dataCollectionPermissions);
            }

            @Override public GeckoResult<AllowOrDeny> onUpdatePrompt(
                    WebExtension extension, String[] permissions, String[] origins,
                    String[] dataCollectionPermissions) {
                return promptPermission("Approve Firefox extension update?",
                        extension, permissions, origins, dataCollectionPermissions);
            }
        });
    }

    private static String extensionTitle(WebExtension extension) {
        if (extension == null || extension.metaData == null) return "Unknown extension";
        String name = extension.metaData.name;
        return (name == null || name.isEmpty() ? "Extension" : name) +
                "\nID: " + extension.id;
    }

    private static String limit(String s) {
        if (s == null) return "";
        return s.length() <= 900 ? s : s.substring(0, 900) + "…";
    }

    private static String describePermissions(String[] permissions, String[] origins,
                                               String[] dataCollection) {
        return "\n\nAPI permissions: " + limit(Arrays.toString(permissions)) +
                "\nWebsite access: " + limit(Arrays.toString(origins)) +
                "\nData collection: " + limit(Arrays.toString(dataCollection)) +
                "\nPrivate browsing: OFF";
    }

    private interface Decision { void complete(boolean approved); }
    private void promptUser(String title, String message, Decision decision) {
        final AtomicBoolean resolved = new AtomicBoolean(false);
        activity.runOnUiThread(() -> {
            if (destroyed || activity.isFinishing() || pendingPrompt != null) {
                decision.complete(false);
                return;
            }
            final AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setTitle(title)
                    .setMessage(message)
                    .setNegativeButton("Deny", (ignored, which) -> {
                        if (resolved.compareAndSet(false, true)) decision.complete(false);
                    })
                    .setPositiveButton("Allow", (ignored, which) -> {
                        if (resolved.compareAndSet(false, true)) decision.complete(true);
                    })
                    .setOnCancelListener(ignored -> {
                        if (resolved.compareAndSet(false, true)) decision.complete(false);
                    })
                    .create();
            pendingPrompt = dialog;
            dialog.setOnDismissListener(ignored -> {
                if (pendingPrompt == dialog) pendingPrompt = null;
                if (resolved.compareAndSet(false, true)) decision.complete(false);
            });
            dialog.show();
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE).requestFocus();
        });
    }

    private GeckoResult<AllowOrDeny> promptPermission(String title, WebExtension extension,
                    String[] permissions, String[] origins, String[] dataCollectionPermissions) {
        final GeckoResult<AllowOrDeny> result = new GeckoResult<>();
        promptUser(title,
                extensionTitle(extension) +
                describePermissions(permissions, origins, dataCollectionPermissions),
                approved -> result.complete(approved ? AllowOrDeny.ALLOW : AllowOrDeny.DENY));
        return result;
    }

    private void report(ActionCallback callback, boolean ok, String message) {
        activity.runOnUiThread(() -> {
            if (!destroyed && !activity.isFinishing()) callback.onResult(ok, message);
        });
    }

    @Override public void listAddons(ListCallback callback) {
        if (destroyed) return;
        controller.list().accept(installed -> {
            List<AddonInfo> output = new ArrayList<>();
            for (WebExtension ext : installed) {
                WebExtension.MetaData meta = ext.metaData;
                output.add(new AddonInfo(ext.id,
                        meta.name == null ? ext.id : meta.name,
                        meta.version, Arrays.toString(meta.requiredPermissions),
                        Arrays.toString(meta.requiredOrigins), meta.enabled, ext.isBuiltIn));
            }
            activity.runOnUiThread(() -> {
                if (!destroyed && !activity.isFinishing()) callback.onResult(output, null);
            });
        }, error -> activity.runOnUiThread(() -> {
            if (!destroyed && !activity.isFinishing())
                callback.onResult(new ArrayList<>(), "Unable to list Firefox extensions");
        }));
    }

    @Override public void installBundledTestAddon(ActionCallback callback) {
        if (destroyed) return;
        // Only an original, no-privilege extension shipped in this APK, scoped to example.com.
        controller.ensureBuiltIn(BUILTIN_URI, BUILTIN_ID).accept(
                addon -> report(callback, true, "Installed " + addon.id),
                error -> report(callback, false, "Bundled Firefox extension installation failed"));
    }

    /** Validates HTTPS remote .xpi URLs, no credentials/fragment/nonstandard scheme. */
    private static boolean validSignedXpiUrl(String value) {
        if (value == null || value.length() > 2048) return false;
        try {
            URI uri = new URI(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null && !uri.getHost().isEmpty()
                    && uri.getRawUserInfo() == null && uri.getFragment() == null
                    && uri.getPath() != null
                    && uri.getPath().toLowerCase(java.util.Locale.ROOT).endsWith(".xpi");
        } catch (URISyntaxException ex) {
            return false;
        }
    }

    @Override public void installSignedXpi(String httpsUrl, ActionCallback callback) {
        if (destroyed) return;
        if (!validSignedXpiUrl(httpsUrl)) {
            report(callback, false, "A valid direct HTTPS URL ending in .xpi is required");
            return;
        }
        // Gecko verifies Mozilla's XPI signature and calls the required permissions prompt.
        controller.install(httpsUrl).accept(
                addon -> report(callback, true, "Installed signed Firefox XPI: " + addon.id),
                error -> report(callback, false, "Mozilla rejected or could not install this XPI"));
    }

    @Override public void setAddonEnabled(String id, boolean enabled,
                                           ActionCallback callback) {
        if (destroyed) return;
        controller.list().accept(installed -> {
            for (WebExtension ext : installed) {
                if (!ext.id.equals(id)) continue;
                GeckoResult<WebExtension> request = enabled
                        ? controller.enable(ext, WebExtensionController.EnableSource.USER)
                        : controller.disable(ext, WebExtensionController.EnableSource.USER);
                request.accept(updated -> report(callback, true,
                                enabled ? "Extension enabled" : "Extension disabled"),
                        error -> report(callback, false, "Cannot change extension state"));
                return;
            }
            report(callback, false, "Extension not found");
        }, error -> report(callback, false, "Unable to retrieve extension"));
    }

    @Override public void uninstallAddon(String id, ActionCallback callback) {
        if (destroyed) return;
        controller.list().accept(installed -> {
            for (WebExtension ext : installed) {
                if (!ext.id.equals(id)) continue;
                controller.uninstall(ext).accept(ignored ->
                                report(callback, true, "Extension uninstalled"),
                        error -> report(callback, false, "Extension uninstall failed"));
                return;
            }
            report(callback, false, "Extension not found");
        }, error -> report(callback, false, "Unable to retrieve extension"));
    }

    void destroy() {
        if (destroyed) return;
        // Fail closed and avoid leaking a prompt UI across Activity recreation.
        if (pendingPrompt != null) pendingPrompt.dismiss();
        pendingPrompt = null;
        controller.setPromptDelegate(null);
        destroyed = true;
    }
}
