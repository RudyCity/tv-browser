package io.rudycity.tvbrowser;

import android.view.View;
import java.util.List;

/**
 * Engine-independent toolbar contract. API 19-safe: no Compose references in the legacy app.
 * The optional Kotlin Compose adapter is loaded only in modern Android variants.
 */
public interface BrowserChrome {
    interface Actions {
        void navigate(String input);
        void back();
        void forward();
        void home();
        void reload();
        void bookmark();
        void extensions();
        void menu();
        /** Optional direct tab-manager trigger; defaults to opening the browser menu. */
        default void tabs() { menu(); }
        /** Optional direct About dialog trigger; defaults to opening the browser menu. */
        default void about() { menu(); }
        /** Direct tab management actions. */
        default void selectTab(int index) {}
        default void closeTab(int index) {}
        default void newTab() {}
        default void newPrivateTab() {}
        default void closeAllTabs() {}
        /** Direct navigation & settings triggers. */
        default void history() { menu(); }
        default void bookmarks() { menu(); }
        default void settings() { menu(); }
    }

    interface ChoiceHandler {
        /** Called only for an explicitly chosen action; dismiss never invokes callbacks. */
        void onChoice(int index);
    }

    interface TabSheetHandler {
        void onSelect(int index);
        void onClose(int index);
        void onNewTab();
        void onNewPrivateTab();
        void onCloseAll();
        default void onDuplicate(int index) {}
    }

    final class ActionItem {
        public final String label;
        public final String supporting;
        public final boolean selected;

        public ActionItem(String label, String supporting, boolean selected) {
            this.label = label;
            this.supporting = supporting;
            this.selected = selected;
        }
    }

    final class HistoryItem {
        public final String title;
        public final String url;
        public final String host;

        public HistoryItem(String title, String url, String host) {
            this.title = title;
            this.url = url;
            this.host = host;
        }
    }

    interface HistoryHandler {
        void onOpen(String url);
        void onOpenInNewTab(String url);
        void onRemove(String url);
        void onClearAll();
    }

    final class BookmarkItem {
        public final String title;
        public final String url;
        public final String host;

        public BookmarkItem(String title, String url, String host) {
            this.title = title;
            this.url = url;
            this.host = host;
        }
    }

    interface BookmarkHandler {
        void onOpen(String url);
        void onOpenInNewTab(String url);
        void onAdd(String title, String url);
        void onRemove(String url);
    }

    final class SettingsState {
        public final boolean darkMode;
        public final String themeMode; // "system", "dark", "light"
        public final boolean desktopMode;
        public final String engineMode;
        public final String activeEngineName;
        public final boolean tvDevice;
        public final boolean pointerMode;
        public final int openTabCount;
        public final int extensionCount;

        public SettingsState(boolean darkMode, boolean desktopMode, String engineMode,
                             String activeEngineName, boolean tvDevice, boolean pointerMode,
                             int openTabCount, int extensionCount) {
            this(darkMode, darkMode ? "dark" : "light", desktopMode, engineMode, activeEngineName, tvDevice, pointerMode, openTabCount, extensionCount);
        }

        public SettingsState(boolean darkMode, String themeMode, boolean desktopMode, String engineMode,
                             String activeEngineName, boolean tvDevice, boolean pointerMode,
                             int openTabCount, int extensionCount) {
            this.darkMode = darkMode;
            this.themeMode = themeMode != null ? themeMode : (darkMode ? "dark" : "light");
            this.desktopMode = desktopMode;
            this.engineMode = engineMode;
            this.activeEngineName = activeEngineName;
            this.tvDevice = tvDevice;
            this.pointerMode = pointerMode;
            this.openTabCount = openTabCount;
            this.extensionCount = extensionCount;
        }
    }

    interface SettingsHandler {
        void onToggleDarkMode(boolean enabled);
        default void onSetThemeMode(String mode) {
            onToggleDarkMode("dark".equalsIgnoreCase(mode));
        }
        void onToggleDesktopMode(boolean enabled);
        void onChangeEngine();
        void onClearBrowsingData();
        void onOpenExtensions();
        void onTogglePointerMode();
        void onShowAbout();
    }

    final class TabItem {
        public final String title;
        public final String host;
        public final boolean active;
        public final boolean privateMode;
        public final boolean loading;
        public final int progress;
        public final boolean crashed;

        public TabItem(String title, String host, boolean active, boolean privateMode,
                       boolean loading, int progress, boolean crashed) {
            this.title = title;
            this.host = host;
            this.active = active;
            this.privateMode = privateMode;
            this.loading = loading;
            this.progress = progress;
            this.crashed = crashed;
        }
    }

    /** Render an accessible Material 3 browser menu; classic/TV shell uses native dialog. */
    void showActions(String title, String[] labels, ChoiceHandler handler);

    /** Rich action list with supporting host/status text and an explicit empty state. */
    default boolean showRichActions(String title, List<ActionItem> items,
                                    String emptyTitle, String emptyDetail,
                                    ChoiceHandler handler) {
        return false;
    }

    /** Material 3 tab manager modal backed by real renderer sessions. */
    default boolean showTabManager(List<TabItem> items, int maxTabs, TabSheetHandler handler) {
        return false;
    }

    /** Material 3 history manager modal with search, open in new tab, and single/batch clear. */
    default boolean showHistoryManager(List<HistoryItem> items, HistoryHandler handler) {
        return false;
    }

    /** Material 3 bookmark manager modal with search, manual add, open in new tab, and deletion. */
    default boolean showBookmarkManager(List<BookmarkItem> items, BookmarkHandler handler) {
        return false;
    }

    final class ExtensionItem {
        public final String id;
        public final String name;
        public final String description;
        public final boolean enabled;
        public final boolean builtIn;
        public final String siteRule;

        public ExtensionItem(String id, String name, String description,
                             boolean enabled, boolean builtIn, String siteRule) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.enabled = enabled;
            this.builtIn = builtIn;
            this.siteRule = siteRule == null ? "inherit" : siteRule;
        }
    }

    interface ExtensionManagerHandler {
        void onToggleExtension(String id, boolean enabled);
        void onSetSiteRule(String id, String rule);
        void onOpenUserscriptEditor();
        void onOpenVideoControls();
        void onResetAllExtensions();
    }

    final class ToolsState {
        public final String currentUrl;
        public final String currentHost;
        public final String engineName;
        public final boolean desktopMode;
        public final boolean darkTheme;
        public final boolean tvDevice;
        public final boolean cursorMode;
        public final boolean canSavePdf;
        public final boolean hasSitePermissions;

        public ToolsState(String currentUrl, String currentHost, String engineName,
                          boolean desktopMode, boolean darkTheme, boolean tvDevice,
                          boolean cursorMode, boolean canSavePdf, boolean hasSitePermissions) {
            this.currentUrl = currentUrl == null ? "" : currentUrl;
            this.currentHost = currentHost == null ? "" : currentHost;
            this.engineName = engineName == null ? "System WebView" : engineName;
            this.desktopMode = desktopMode;
            this.darkTheme = darkTheme;
            this.tvDevice = tvDevice;
            this.cursorMode = cursorMode;
            this.canSavePdf = canSavePdf;
            this.hasSitePermissions = hasSitePermissions;
        }
    }

    interface ToolsHandler {
        void onZoomIn();
        void onZoomOut();
        void onScrollDown();
        void onScrollUp();
        void onSavePdf();
        void onFindInPage();
        void onSitePermissions();
        void onVideoControls();
        void onChangeEngine();
        void onToggleDesktop(boolean enabled);
        void onToggleDarkMode(boolean enabled);
        void onTogglePointerMode();
        void onClearBrowsingData();
        void onShowMemoryAndTabs();
        void onFocusAddress();
    }

    /** Structured browser settings modal organized into categorized sections. */
    default boolean showSettings(SettingsState state, SettingsHandler handler) {
        return false;
    }

    /** Material 3 extension management modal with toggle switches, site permissions, and scripts. */
    default boolean showExtensionManager(List<ExtensionItem> items, String currentHost,
                                         ExtensionManagerHandler handler) {
        return false;
    }

    /** Material 3 browser tools hub modal organizing page view, media, engine, and diagnostics. */
    default boolean showToolsManager(ToolsState state, ToolsHandler handler) {
        return false;
    }

    /** Explicit affirmative action only; dismiss and Cancel must never execute it. */
    void showConfirmation(String title, String message, String actionLabel, Runnable onConfirm);

    /** Structured security/consent confirmation with explicit origin & destination readout. */
    default boolean showSecurityConfirmation(String title, String summary,
                                             String sourceLabel, String destinationLabel,
                                             String warning, String safeDismissLabel,
                                             String confirmLabel, boolean destructive,
                                             Runnable onConfirm) {
        return false;
    }

    /** Material 3 About modal displaying real build, runtime, and renderer metadata. */
    default boolean showAbout(String appName, String versionName, String packageId,
                              String flavor, int androidSdk, String activeEngine,
                              String engineDetail, boolean cleartextAllowed, int openTabs) {
        return false;
    }

    View view();
    /** Presentation-only theme, never affects website DOM or renderer settings. */
    default void setDarkTheme(boolean dark) {}
    /** Open omnibox for text input, including from the old advanced tools menu. */
    void focusAddress();

    /**
     * Full URL is for editing only; the Compose toolbar renders a compact host otherwise.
     * Call from the Android UI thread, never directly from the WebView background worker.
     */
    void update(String url, boolean loading, int progress,
                boolean canGoBack, boolean canGoForward, boolean bookmarked);

    /** Overload including live tab count for responsive tab indicators. */
    default void update(String url, boolean loading, int progress,
                        boolean canGoBack, boolean canGoForward, boolean bookmarked,
                        int tabCount) {
        update(url, loading, progress, canGoBack, canGoForward, bookmarked);
    }

    /** Live tab list synchronization for horizontal tab strip and tab management. */
    default void updateTabs(List<TabItem> items, int maxTabs) {}
}
