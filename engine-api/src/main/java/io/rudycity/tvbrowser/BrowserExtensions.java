package io.rudycity.tvbrowser;

import java.util.List;

/**
 * Real Firefox add-on capability, only exposed when the active engine is Gecko.
 * This contract contains no Gecko classes and is safe in the API19 legacy APK.
 */
public interface BrowserExtensions {
    final class AddonInfo {
        public final String id;
        public final String name;
        public final String version;
        public final String requiredPermissions;
        public final String requiredOrigins;
        public final boolean enabled;
        public final boolean builtIn;
        public AddonInfo(String id, String name, String version, String requiredPermissions,
                         String requiredOrigins, boolean enabled, boolean builtIn) {
            this.id = id;
            this.name = name;
            this.version = version;
            this.requiredPermissions = requiredPermissions;
            this.requiredOrigins = requiredOrigins;
            this.enabled = enabled;
            this.builtIn = builtIn;
        }
    }
    interface ListCallback {
        void onResult(List<AddonInfo> installed, String error);
    }
    interface ActionCallback {
        void onResult(boolean successful, String message);
    }
    void listAddons(ListCallback callback);
    void installBundledTestAddon(ActionCallback callback);
    void installSignedXpi(String httpsUrl, ActionCallback callback);
    void setAddonEnabled(String id, boolean enabled, ActionCallback callback);
    void uninstallAddon(String id, ActionCallback callback);
}
