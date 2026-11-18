package io.rudycity.tvbrowser;

import android.app.Activity;
import androidx.activity.ComponentActivity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Insets;
import android.view.WindowInsets;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.EditorInfo;
import android.webkit.URLUtil;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Responsive Android phone, tablet, and TV shell with explicit engine fallback. */
public final class MainActivity extends ComponentActivity {
    private static final String PREFERENCES = "tv_browser";
    private static final String BOOKMARKS = "bookmarks";
    private static final String HISTORY = "history";
    private static final String DESKTOP = "desktop_mode";
    private static final String DARK_THEME = "dark_mode";
    private static final String ENGINE = "render_engine";
    private static final String RESTORE_TABS = "restorable_tab_urls";
    private static final String RESTORE_SELECTED = "restorable_tab_selected";
    // Restrained browser chrome: pages keep their own styling and full viewport.
    private static final int TOOLBAR_BG = Color.rgb(23, 30, 41);
    private static final int OMNIBOX_BG = Color.rgb(39, 49, 64);
    private static final int HIGHLIGHT = Color.rgb(62, 110, 176);

    private FrameLayout root;
    private LinearLayout primaryRow;
    private LinearLayout secondaryRow;
    private Button backButton;
    private Button forwardButton;
    private Button homeButton;
    private Button reloadButton;
    private Button goButton;
    private Button extensionsButton;
    private Button pointerButton;
    private Button menuButton;
    private boolean tvDevice;
    private BrowserEngine engine;
    // Each real tab owns its own GeckoSession or WebView engine. Background tabs are
    // paused/hidden, never replaced by a synthetic tab counter.
    private static final class BrowserTab {
        BrowserEngine browser;
        String reason;
        String title;
        String url;
        boolean home = true;
        boolean loading;
        boolean crashed;
        int progress;
        boolean privateMode;
    }
    private final List<BrowserTab> tabs = new ArrayList<>();
    private BrowserTab activeTab;
    private FrameLayout engineSlot;
    private String engineReason;
    private EditText address;
    private TextView status;
    private ProgressBar loadingProgress;
    private BrowserChrome composeChrome;
    private BrowserHome composeHome;
    /** Explicitly requested local landing; stale WebView callbacks must not reopen it. */
    private boolean homeRequested;
    private boolean pageLoading;
    private int pageProgress;
    private Button bookmarkButton;
    private SharedPreferences prefs;
    private View fullscreenView;
    private Runnable fullscreenClose;
    private int recoveryAttempts;
    private AlertDialog popupDialog;
    private AlertDialog downloadDialog;
    private TvCursorOverlay cursorOverlay;
    private boolean cursorMode;
    private View toolbarStack;
    private boolean geckoFullscreen;
    private boolean restoringSession = true;
    private boolean clearingBrowserData;


    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tvDevice = detectTvDevice();
        updateSystemBars();
        prefs = getSharedPreferences(PREFERENCES, MODE_PRIVATE);
        setUpUi();
        setUpBrowser();
        String launch = getIntent() == null ? null : getIntent().getDataString();
        String restored = savedInstanceState == null ? null :
                savedInstanceState.getString("current_url");
        if (UrlTools.isWebUrl(launch)) open(launch);
        else if (savedInstanceState != null &&
                savedInstanceState.containsKey("restorable_tabs")) {
            ArrayList<String> urls =
                    savedInstanceState.getStringArrayList("restorable_tabs");
            if (urls != null && !urls.isEmpty()) {
                final int count = Math.min(urls.size(), maxTabs());
                for (int i = 0; i < count; i++) {
                    if (i > 0 && !createBrowserTab(prefs.getString(ENGINE, EnginePolicy.AUTO),
                            null)) break;
                    if (UrlTools.isWebUrl(urls.get(i))) open(urls.get(i));
                    else home();
                }
                switchBrowserTab(Math.min(
                        savedInstanceState.getInt("selected_tab", 0), tabs.size() - 1));
            }
        } else if (UrlTools.isWebUrl(restored)) open(restored);
        else if (savedInstanceState == null) restorePersistedTabs();
        restoringSession = false;
        persistTabs();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && UrlTools.isWebUrl(intent.getDataString())) open(intent.getDataString());
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        if (activeTab != null && !activeTab.privateMode &&
                engine != null && UrlTools.isWebUrl(engine.currentUrl()))
            outState.putString("current_url", engine.currentUrl());
        ArrayList<String> urls = new ArrayList<>();
        int selected = 0;
        for (BrowserTab tab : tabs) {
            if (tab.privateMode) continue;
            if (tab == activeTab) selected = urls.size();
            String url = tab.browser == null ? null : tab.browser.currentUrl();
            urls.add(UrlTools.isWebUrl(url) ? url : "");
        }
        outState.putStringArrayList("restorable_tabs", urls);
        outState.putInt("selected_tab", selected);
        super.onSaveInstanceState(outState);
    }

    /** Persists only eligible URLs; never form inputs, cookies or engine state. */
    private void persistTabs() {
        if (restoringSession || clearingBrowserData || tabs.isEmpty()) return;
        JSONArray urls = new JSONArray();
        int selected = 0;
        for (BrowserTab tab : tabs) {
            // Private URLs and tab counts must never be written to persistent storage.
            if (tab.privateMode) continue;
            if (tab == activeTab) selected = urls.length();
            String url = tab.browser == null ? null : tab.browser.currentUrl();
            if (!UrlTools.isWebUrl(url)) url = tab.url;
            urls.put(UrlTools.isWebUrl(url) ? url : "");
        }
        prefs.edit().putString(RESTORE_TABS, urls.toString())
                .putInt(RESTORE_SELECTED, selected).apply();
    }

    private void restorePersistedTabs() {
        try {
            JSONArray urls = new JSONArray(prefs.getString(RESTORE_TABS, "[]"));
            if (urls.length() == 0) return;
            clearAllTabSessions();
            int count = Math.min(urls.length(), maxTabs());
            for (int i = 0; i < count; i++) {
                String url = urls.optString(i, "");
                if (!createBrowserTab(prefs.getString(ENGINE, EnginePolicy.AUTO),
                        UrlTools.isWebUrl(url) ? url : null)) break;
            }
            if (!tabs.isEmpty()) switchBrowserTab(Math.min(
                    Math.max(0, prefs.getInt(RESTORE_SELECTED, 0)), tabs.size() - 1));
        } catch (JSONException corrupt) {
            prefs.edit().remove(RESTORE_TABS).remove(RESTORE_SELECTED).apply();
        }
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        tvDevice = detectTvDevice();
        updateSystemBars();
        arrangeToolbar();
    }

    private boolean detectTvDevice() {
        Configuration config = getResources().getConfiguration();
        boolean televisionUi = (config.uiMode & Configuration.UI_MODE_TYPE_MASK)
                == Configuration.UI_MODE_TYPE_TELEVISION;
        return BrowserLayoutPolicy.isTelevision(
                televisionUi,
                getPackageManager().hasSystemFeature("android.software.leanback"),
                getPackageManager().hasSystemFeature("android.hardware.type.television"),
                getPackageManager().hasSystemFeature("android.hardware.touchscreen"));
    }

    private void updateSystemBars() {
        if (tvDevice || geckoFullscreen) getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (root != null && Build.VERSION.SDK_INT >= 35) root.requestApplyInsets();
    }

    /** Android 15+ edge-to-edge is mandatory for target SDK 35+; protect the
     * omnibox and TV controls while retaining full-bleed video when requested. */
    private void installSystemInsets() {
        if (Build.VERSION.SDK_INT < 35 ||
                getApplicationInfo().targetSdkVersion < 35 || root == null) return;
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (tvDevice || geckoFullscreen) {
                view.setPadding(0, 0, 0, 0);
            } else {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            }
            return insets;
        });
        root.requestApplyInsets();
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable buttonBackground(boolean selected) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(dp(tvDevice ? 13 : 22));
        drawable.setColor(selected ? HIGHLIGHT :
                (tvDevice ? Color.rgb(37, 50, 68) : TOOLBAR_BG));
        if (tvDevice || selected) {
            drawable.setStroke(dp(selected ? 2 : 1),
                    selected ? Color.rgb(173, 209, 255) : Color.rgb(65, 81, 102));
        }
        return drawable;
    }

    private GradientDrawable omniboxBackground(boolean selected) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(dp(tvDevice ? 14 : 24));
        drawable.setColor(OMNIBOX_BG);
        drawable.setStroke(dp(selected ? 2 : 1),
                selected ? Color.rgb(143, 191, 249) : Color.rgb(51, 64, 82));
        return drawable;
    }

    private Button addButton(LinearLayout toolbar, String text, String description,
                             View.OnClickListener action) {
        final Button b = new Button(this);
        b.setText(text);
        b.setContentDescription(description);
        b.setTextSize(tvDevice ? 20 : 23);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setPadding(0, 0, 0, 0);
        b.setMinimumWidth(0);
        b.setMinimumHeight(0);
        b.setBackgroundDrawable(buttonBackground(false));
        toolbar.addView(b);
        b.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean hasFocus) {
                b.setBackgroundDrawable(buttonBackground(hasFocus));
            }
        });
        b.setOnClickListener(action);
        return b;
    }

    private void setUpUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        root.addView(layout, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout toolbarStack = new LinearLayout(this);
        this.toolbarStack = toolbarStack;
        toolbarStack.setOrientation(LinearLayout.VERTICAL);
        toolbarStack.setBackgroundColor(TOOLBAR_BG);
        toolbarStack.setPadding(dp(tvDevice ? 6 : 8), dp(tvDevice ? 5 : 3),
                dp(tvDevice ? 6 : 8), dp(tvDevice ? 5 : 3));
        layout.addView(toolbarStack, new LinearLayout.LayoutParams(-1, -2));
        primaryRow = new LinearLayout(this);
        primaryRow.setGravity(Gravity.CENTER_VERTICAL);
        secondaryRow = new LinearLayout(this);
        secondaryRow.setGravity(Gravity.CENTER_VERTICAL);
        toolbarStack.addView(primaryRow,
                new LinearLayout.LayoutParams(-1, dp(tvDevice ? 58 : 52)));
        toolbarStack.addView(secondaryRow, new LinearLayout.LayoutParams(-1, dp(58)));

        backButton = addButton(primaryRow, "‹", "Back", new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (engine != null && engine.canGoBack()) engine.goBack();
            }
        });
        forwardButton = addButton(primaryRow, "›", "Forward", new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (engine != null && engine.canGoForward()) engine.goForward();
            }
        });
        homeButton = addButton(primaryRow, "⌂", "Home", new View.OnClickListener() {
            @Override public void onClick(View v) { home(); }
        });
        reloadButton = addButton(primaryRow, "↻", "Refresh", new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (engine != null) engine.reload();
            }
        });

        address = new EditText(this);
        address.setSingleLine(true);
        address.setHint("Search or enter address");
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        address.setTextSize(tvDevice ? 19 : 16);
        address.setTextColor(Color.WHITE);
        address.setHintTextColor(Color.rgb(166, 180, 195));
        address.setSelectAllOnFocus(true);
        address.setPadding(dp(16), 0, dp(14), 0);
        address.setBackgroundDrawable(omniboxBackground(false));
        primaryRow.addView(address);
        address.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean hasFocus) {
                address.setBackgroundDrawable(omniboxBackground(hasFocus));
                if (hasFocus) {
                    String current = engine == null ? null : engine.currentUrl();
                    if (UrlTools.isWebUrl(current)) address.setText(current);
                    address.selectAll();
                } else {
                    showCompactAddress();
                }
            }
        });
        address.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                submitAddress();
                return true;
            }
        });
        goButton = addButton(primaryRow, "Go", "Navigate to entered address",
                new View.OnClickListener() {
            @Override public void onClick(View v) { submitAddress(); }
        });
        bookmarkButton = addButton(primaryRow, "☆", "Toggle bookmark",
                new View.OnClickListener() {
            @Override public void onClick(View v) { toggleBookmark(); }
        });
        extensionsButton = addButton(primaryRow, "Ext", "Extensions manager",
                new View.OnClickListener() {
            @Override public void onClick(View v) { showExtensions(); }
        });
        pointerButton = addButton(primaryRow, "Ptr", "Toggle TV remote pointer mode",
                new View.OnClickListener() {
            @Override public void onClick(View v) { toggleCursorMode(); }
        });
        menuButton = addButton(primaryRow, "⋮", "Browser menu", new View.OnClickListener() {
            @Override public void onClick(View v) { showMenu(); }
        });
        arrangeToolbar();
        attachComposeChrome(toolbarStack, layout);

        engineSlot = new FrameLayout(this);
        engineSlot.setBackgroundColor(Color.rgb(12, 20, 30));
        cursorOverlay = new TvCursorOverlay(this);
        engineSlot.addView(cursorOverlay, new FrameLayout.LayoutParams(-1, -1));
        attachComposeHome();
        loadingProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        loadingProgress.setMax(100);
        loadingProgress.setVisibility(View.GONE);
        if (Build.VERSION.SDK_INT >= 21) {
            loadingProgress.setProgressTintList(ColorStateList.valueOf(
                    Color.rgb(139, 188, 246)));
            loadingProgress.setProgressBackgroundTintList(ColorStateList.valueOf(TOOLBAR_BG));
        }
        layout.addView(loadingProgress, new LinearLayout.LayoutParams(-1, dp(2)));
        layout.addView(engineSlot, new LinearLayout.LayoutParams(-1, 0, 1));

        status = new TextView(this);
        status.setTextColor(Color.rgb(181, 196, 214));
        status.setBackgroundColor(TOOLBAR_BG);
        status.setPadding(dp(12), dp(4), dp(12), dp(4));
        status.setSingleLine(true);
        status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        status.setTextSize(tvDevice ? 15 : 12);
        status.setText(tvDevice ? "D-pad: select • OK: open • Menu: options"
                : "Swipe to browse • Menu: options");
        status.setVisibility(tvDevice ? View.VISIBLE : View.GONE);
        layout.addView(status, new LinearLayout.LayoutParams(-1, dp(26)));
        setContentView(root);
        installSystemInsets();
        syncComposeChrome();
    }

    /** Modern variants load the Kotlin/Compose module; legacy API 19 never loads it. */
    private void attachComposeChrome(LinearLayout legacyToolbar, LinearLayout parent) {
        if (!BrowserPresentationPolicy.shouldTryCompose(tvDevice,
                Build.VERSION.SDK_INT, !"legacy".equals(BuildConfig.FLAVOR))) return;
        View candidateView = null;
        try {
            Class<?> implementation = Class.forName(
                    "io.rudycity.tvbrowser.compose.ComposeBrowserToolbar");
            BrowserChrome chrome = (BrowserChrome) implementation
                    .getConstructor(Activity.class, BrowserChrome.Actions.class)
                    .newInstance(this, new BrowserChrome.Actions() {
                        @Override public void navigate(String input) { navigateFromCompose(input); }
                        @Override public void back() {
                            if (engine != null && engine.canGoBack()) engine.goBack();
                        }
                        @Override public void forward() {
                            if (engine != null && engine.canGoForward()) engine.goForward();
                        }
                        @Override public void home() { MainActivity.this.home(); }
                        @Override public void reload() {
                            if (engine != null) engine.reload();
                        }
                        @Override public void bookmark() { toggleBookmark(); }
                        @Override public void extensions() { showExtensions(); }
                        @Override public void menu() { showMenu(); }
                    });
            candidateView = chrome.view();
            parent.addView(candidateView, 1, new LinearLayout.LayoutParams(-1, -2));
            composeChrome = chrome;
            legacyToolbar.setVisibility(View.GONE);
            updateJavaProgress();
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            // A partial Compose view must never obscure the working Java fallback.
            if (candidateView != null && candidateView.getParent() == parent) {
                parent.removeView(candidateView);
            }
            android.util.Log.w("RudyBrowser", "Compose presentation unavailable; legacy UI used", failure);
            composeChrome = null;
            legacyToolbar.setVisibility(View.VISIBLE);
            root.post(new Runnable() {
                @Override public void run() {
                    toast("Modern UI unavailable. Using classic browser controls.");
                }
            });
        }
    }

    /** Never show the legacy progress strip underneath Compose's own loading line. */
    private void updateJavaProgress() {
        if (loadingProgress == null) return;
        boolean visible = BrowserPresentationPolicy.showJavaProgress(
                composeChrome != null, pageLoading);
        loadingProgress.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) loadingProgress.setProgress(Math.max(1, pageProgress));
    }

    /** Draw the homepage with Material 3 instead of relying on flaky old WebView glyphs. */
    private void attachComposeHome() {
        if (composeChrome == null || tvDevice || Build.VERSION.SDK_INT < 21) return;
        try {
            Class<?> implementation = Class.forName(
                    "io.rudycity.tvbrowser.compose.ComposeBrowserHome");
            BrowserHome home = (BrowserHome) implementation
                    .getConstructor(Activity.class, BrowserHome.Actions.class)
                    .newInstance(this, new BrowserHome.Actions() {
                        @Override public void openWebsite(String httpsUrl) {
                            if (UrlTools.isWebUrl(httpsUrl)) open(httpsUrl);
                        }
                        @Override public void focusAddress() {
                            MainActivity.this.focusAddress();
                        }
                    });
            View landing = home.view();
            landing.setVisibility(View.GONE);
            engineSlot.addView(landing, new FrameLayout.LayoutParams(-1, -1));
            composeHome = home;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException problem) {
            android.util.Log.w("RudyBrowser", "Native Compose landing unavailable", problem);
            composeHome = null; // WebView home remains usable as a fallback.
        }
    }

    private void showComposeHome(boolean visible) {
        if (composeHome != null) composeHome.view().setVisibility(
                visible ? View.VISIBLE : View.GONE);
    }

    private void navigateFromCompose(String input) {
        try {
            String url = UrlTools.navigate(input);
            if (url != null) open(url);
        } catch (IllegalArgumentException invalid) {
            toast(invalid.getMessage());
        }
    }

    private void syncComposeChrome() {
        if (composeChrome == null) return;
        String url = engine == null ? null : engine.currentUrl();
        composeChrome.update(url, pageLoading, pageProgress,
                engine != null && engine.canGoBack(),
                engine != null && engine.canGoForward(),
                UrlTools.isWebUrl(url) && isBookmarked(url));
    }

    private void addToolbarButton(LinearLayout row, Button button, int sizeDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp));
        params.setMargins(dp(tvDevice ? 2 : 1), dp(2),
                dp(tvDevice ? 2 : 1), dp(2));
        row.addView(button, params);
    }

    private void addAddressBar(LinearLayout row, int heightDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(heightDp), 1);
        params.setMargins(dp(tvDevice ? 5 : 3), dp(2),
                dp(tvDevice ? 5 : 3), dp(2));
        row.addView(address, params);
    }

    /** Responsive layout without recreating WebView, history, cookies or the address field. */
    private void arrangeToolbar() {
        if (primaryRow == null || secondaryRow == null) return;
        int widthDp = getResources().getConfiguration().screenWidthDp;
        if (widthDp <= 0) widthDp = (int) (getResources().getDisplayMetrics().widthPixels /
                getResources().getDisplayMetrics().density);
        int mode = BrowserLayoutPolicy.toolbarMode(widthDp, tvDevice);

        primaryRow.removeAllViews();
        secondaryRow.removeAllViews();

        if (mode == BrowserLayoutPolicy.MOBILE) {
            // Chrome-inspired compact phone: no duplicate navigation or fake tab button.
            addToolbarButton(primaryRow, backButton, 44);
            addAddressBar(primaryRow, 46);
            addToolbarButton(primaryRow, menuButton, 44);
        } else if (mode == BrowserLayoutPolicy.TABLET) {
            addToolbarButton(primaryRow, backButton, 44);
            addToolbarButton(primaryRow, forwardButton, 44);
            if (widthDp >= 720) addToolbarButton(primaryRow, homeButton, 44);
            addAddressBar(primaryRow, 46);
            addToolbarButton(primaryRow, reloadButton, 44);
            addToolbarButton(primaryRow, bookmarkButton, 44);
            if (widthDp >= 840) addToolbarButton(primaryRow, extensionsButton, 44);
            addToolbarButton(primaryRow, menuButton, 44);
        } else {
            // TV retains larger focusable targets and an optional second row.
            boolean compactTv = mode == BrowserLayoutPolicy.TV_COMPACT;
            int size = compactTv ? 48 : 52;
            addToolbarButton(primaryRow, backButton, size);
            addToolbarButton(primaryRow, forwardButton, size);
            if (!compactTv) {
                addToolbarButton(primaryRow, homeButton, size);
                addToolbarButton(primaryRow, reloadButton, size);
            }
            addAddressBar(primaryRow, size);
            addToolbarButton(primaryRow, goButton, size);
            LinearLayout actions = compactTv ? secondaryRow : primaryRow;
            if (compactTv) {
                addToolbarButton(actions, homeButton, size);
                addToolbarButton(actions, reloadButton, size);
            }
            addToolbarButton(actions, bookmarkButton, size);
            addToolbarButton(actions, extensionsButton, size);
            if (!compactTv || widthDp >= 360)
                addToolbarButton(actions, pointerButton, size);
            addToolbarButton(actions, menuButton, size);
        }

        secondaryRow.setVisibility(BrowserLayoutPolicy.hasSecondRow(mode)
                ? View.VISIBLE : View.GONE);
        if (status != null) status.setVisibility(BrowserLayoutPolicy.hasStatusFooter(mode)
                ? View.VISIBLE : View.GONE);
        if (goButton != null) goButton.setTextSize(16);
        if (extensionsButton != null) extensionsButton.setTextSize(15);
        if (pointerButton != null) pointerButton.setTextSize(15);
    }

    private final BrowserEngine.Listener rendererEvents = new BrowserEngine.Listener() {
        @Override public void onPageStarted(String url) {
            if (UrlTools.isWebUrl(url)) {
                homeRequested = false;
                showComposeHome(false);
            }
            pageLoading = true;
            pageProgress = 5;
            updateJavaProgress();
            status.setText("Loading…");
            syncComposeChrome();
        }
        @Override public void onPageFinished(String url, String title) {
            // A late data:/about: callback from the previous home document
            // must not cover the website after a user opens an HTTPS link.
            if (UrlTools.isWebUrl(url)) {
                homeRequested = false;
                showComposeHome(false);
            } else if (homeRequested) {
                showComposeHome(true);
            }
            if (UrlTools.isWebUrl(url)) {
                if (!address.hasFocus()) {
                    address.setText(url);
                    showCompactAddress();
                }
                if (activeTab == null || !activeTab.privateMode) saveHistory(url, title);
            }
            pageLoading = false;
            pageProgress = 100;
            updateJavaProgress();
            refreshBookmarkButton();
            status.setText(cursorMode ? "Remote pointer: arrows move, OK clicks, Back exits"
                    : engine.name() + (tvDevice ? "  •  D-pad: navigate  •  Menu: options" :
                    "  •  Swipe to browse  •  Menu: options"));
        }
        @Override public void onProgress(int progress) {
            pageProgress = progress;
            pageLoading = progress < 100;
            if (progress < 100) {
                if (engine != null) status.setText("Loading " + progress + "%  •  " + engine.name());
            }
            updateJavaProgress();
            syncComposeChrome();
        }
        @Override public void onError(String message) {
            pageLoading = false;
            updateJavaProgress();
            syncComposeChrome();
            status.setText(message);
            toast(message);
        }
        @Override public void onFullscreenRequested(View view, Runnable closeAction) {
            if (fullscreenView != null) { closeAction.run(); return; }
            fullscreenView = view;
            fullscreenClose = closeAction;
            root.addView(view, new FrameLayout.LayoutParams(-1, -1));
            view.requestFocus();
        }
        @Override public void onFullscreenEnded() { hideFullscreen(); }
        @Override public void onFullscreenChanged(boolean fullscreen) {
            setGeckoFullscreen(fullscreen);
        }
        @Override public void onDownloadRequested(String url, String agent, String disposition, String mimeType) {
            download(url, agent, disposition, mimeType);
        }
        @Override public void onPopupRequested(String openerUrl, String destinationUrl) {
            confirmPopup(openerUrl, destinationUrl);
        }
        @Override public void onEngineCrashed(String reason) {
            final BrowserTab crashedTab = activeTab;
            if (crashedTab == null || crashedTab.browser != engine) return;
            if (!BrowserSessionPolicy.canAutoFallback(crashedTab.privateMode, 1)) {
                // A private Gecko process must NEVER silently reopen in WebView,
                // where it could disclose the page to shared cookies or history.
                status.setText("Private tab crashed — it cannot be recovered in shared WebView");
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Private tab unavailable")
                        .setMessage("This private renderer crashed. The private page will not " +
                                "be opened in another browser engine. Close this tab to continue.")
                        .setNegativeButton("Keep closed renderer", null)
                        .setPositiveButton("Close private tab", (dialog, which) -> {
                            int index = tabs.indexOf(crashedTab);
                            if (index >= 0) closeBrowserTab(index);
                        }).show();
                return;
            }
            if (!BrowserSessionPolicy.canAutoFallback(false, ++recoveryAttempts)) {
                status.setText(reason + " — recovery stopped to avoid a crash loop");
                return;
            }
            status.setText(reason + " — switching to WebView…");
            String remembered = null;
            try { if (engine != null) remembered = engine.currentUrl(); }
            catch (RuntimeException ignored) { /* crash recovery still proceeds */ }
            final String url = remembered;
            engineSlot.post(() -> {
                // A delayed crash from a closed/switched session may not steal focus.
                if (activeTab == crashedTab && engine == crashedTab.browser)
                    selectEngine(EnginePolicy.WEBVIEW, url);
            });
        }
    };

    private void setUpBrowser() {
        selectEngine(prefs.getString(ENGINE, EnginePolicy.AUTO), null);
    }

    private int maxTabs() {
        // TV and old devices: smaller memory budget. Compatible touch: capped at 6.
        return tvDevice || Build.VERSION.SDK_INT < 26 ? 3 : 6;
    }

    /** Ignore callbacks for background sessions: no inactive tab can open a dialog. */
    private BrowserEngine.Listener guardedEvents(final BrowserTab tab) {
        return new BrowserEngine.Listener() {
            private boolean active() {
                return activeTab == tab && engine == tab.browser;
            }
            @Override public void onPageStarted(String url) {
                tab.url = UrlTools.isWebUrl(url) ? url : null;
                tab.home = !UrlTools.isWebUrl(url);
                tab.loading = true;
                tab.progress = 5;
                persistTabs();
                if (active()) rendererEvents.onPageStarted(url);
            }
            @Override public void onPageFinished(String url, String title) {
                tab.url = UrlTools.isWebUrl(url) ? url : null;
                tab.title = title;
                tab.loading = false;
                tab.progress = 100;
                persistTabs();
                if (active()) rendererEvents.onPageFinished(url, title);
            }
            @Override public void onProgress(int progress) {
                tab.progress = progress;
                tab.loading = progress < 100;
                if (active()) rendererEvents.onProgress(progress);
            }
            @Override public void onError(String message) {
                tab.loading = false;
                if (active()) rendererEvents.onError(message);
            }
            @Override public void onFullscreenRequested(View view, Runnable closeAction) {
                if (active()) rendererEvents.onFullscreenRequested(view, closeAction);
                else closeAction.run();
            }
            @Override public void onFullscreenEnded() {
                if (active()) rendererEvents.onFullscreenEnded();
            }
            @Override public void onFullscreenChanged(boolean fullscreen) {
                if (active()) rendererEvents.onFullscreenChanged(fullscreen);
                else if (fullscreen) tab.browser.exitFullscreen();
            }
            @Override public void onDownloadRequested(
                    String url, String agent, String disposition, String mimeType) {
                // Background tabs may not spawn unattended download prompts.
                if (active()) rendererEvents.onDownloadRequested(
                        url, agent, disposition, mimeType);
            }
            @Override public void onPopupRequested(String opener, String destination) {
                if (active()) rendererEvents.onPopupRequested(opener, destination);
            }
            @Override public void onEngineCrashed(String reason) {
                tab.crashed = true;
                if (active()) rendererEvents.onEngineCrashed(reason);
            }
        };
    }

    private void clearAllTabSessions() {
        if (activeTab != null) hideFullscreen();
        activeTab = null;
        engine = null;
        for (BrowserTab tab : new ArrayList<>(tabs)) {
            if (tab.browser == null) continue;
            engineSlot.removeView(tab.browser.view());
            tab.browser.destroy();
        }
        tabs.clear();
        homeRequested = false;
        showComposeHome(false);
    }

    private boolean createBrowserTab(String preference, String restoreUrl) {
        return createBrowserTab(preference, restoreUrl, false);
    }

    private boolean createBrowserTab(String preference, String restoreUrl,
                                     boolean privateMode) {
        if (clearingBrowserData) return false;
        if (tabs.size() >= maxTabs()) {
            toast("Tab limit reached; close a background tab to free memory");
            return false;
        }
        BrowserTab tab = new BrowserTab();
        final EngineFactory.Selection selected;
        try {
            selected = privateMode
                    ? EngineFactory.createPrivate(this, prefs.getBoolean(DESKTOP, false),
                            guardedEvents(tab))
                    : EngineFactory.create(this, preference,
                            prefs.getBoolean(DESKTOP, false), guardedEvents(tab));
        } catch (RuntimeException | LinkageError problem) {
            status.setText(privateMode ? "Private Gecko tab unavailable on this device"
                    : "Unable to start a new browser tab");
            return false;
        }
        tab.browser = selected.engine;
        tab.privateMode = privateMode;
        tab.reason = selected.reason;
        BrowserTab previous = activeTab;
        if (previous != null) {
            previous.home = homeRequested;
            previous.loading = pageLoading;
            previous.progress = pageProgress;
            previous.browser.onPause();
            previous.browser.view().setVisibility(View.GONE);
        }
        if (popupDialog != null) popupDialog.dismiss();
        if (downloadDialog != null) downloadDialog.dismiss();
        hideFullscreen();
        activeTab = tab;
        engine = tab.browser;
        engineReason = tab.reason;
        tabs.add(tab);
        engineSlot.addView(engine.view(), 0, new FrameLayout.LayoutParams(-1, -1));
        engine.view().setVisibility(View.VISIBLE);
        engine.view().requestFocus();
        homeRequested = true;
        pageLoading = false;
        pageProgress = 0;
        if (restoreUrl != null && UrlTools.isWebUrl(restoreUrl)) open(restoreUrl);
        else home();
        updateJavaProgress();
        syncComposeChrome();
        status.setText((tab.privateMode ? "Private • " : "") + engine.name() +
                " • tab " + tabs.size() + "/" + maxTabs());
        engine.onResume();
        persistTabs();
        return true;
    }

    private void switchBrowserTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        BrowserTab selected = tabs.get(index);
        if (selected == activeTab) return;
        if (selected.crashed) {
            new AlertDialog.Builder(this)
                    .setTitle("Tab renderer unavailable")
                    .setMessage("This tab's rendering process crashed. Close the tab or " +
                            "switch to another tab; it will not reload silently.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Close crashed tab",
                            (dialog, which) -> closeBrowserTab(index)).show();
            return;
        }
        if (popupDialog != null) popupDialog.dismiss();
        if (downloadDialog != null) downloadDialog.dismiss();
        hideFullscreen();
        cursorMode = false;
        if (cursorOverlay != null) cursorOverlay.setVisibility(View.GONE);
        if (activeTab != null) {
            activeTab.home = homeRequested;
            activeTab.loading = pageLoading;
            activeTab.progress = pageProgress;
            activeTab.browser.onPause();
            activeTab.browser.view().setVisibility(View.GONE);
        }
        activeTab = selected;
        engine = selected.browser;
        engineReason = selected.reason;
        engine.view().setVisibility(View.VISIBLE);
        engine.onResume();
        homeRequested = selected.home;
        pageLoading = selected.loading;
        pageProgress = selected.progress;
        showComposeHome(homeRequested);
        String url = engine.currentUrl();
        address.setText(UrlTools.isWebUrl(url) ? url : "");
        showCompactAddress();
        refreshBookmarkButton();
        updateJavaProgress();
        syncComposeChrome();
        status.setText((selected.privateMode ? "Private • " : "") + engine.name() +
                " • tab " + (index + 1) + "/" + tabs.size());
        engine.view().requestFocus();
        persistTabs();
    }

    private void closeBrowserTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        if (popupDialog != null) popupDialog.dismiss();
        if (downloadDialog != null) downloadDialog.dismiss();
        BrowserTab tab = tabs.remove(index);
        boolean wasActive = tab == activeTab;
        if (wasActive) {
            hideFullscreen();
            activeTab = null;
            engine = null;
        }
        engineSlot.removeView(tab.browser.view());
        tab.browser.destroy();
        if (tabs.isEmpty()) {
            // Closing the last tab opens one clean home, not a fictitious tab 0.
            createBrowserTab(prefs.getString(ENGINE, EnginePolicy.AUTO), null);
        } else if (wasActive) {
            switchBrowserTab(Math.min(index, tabs.size() - 1));
        }
        persistTabs();
    }

    /** Visual tab manager: real renderer sessions, not a synthetic counter. */
    private void showBrowserTabs() {
        if (tabs.isEmpty()) return;
        final int widthDp = Math.max(320,
                getResources().getConfiguration().screenWidthDp);
        final int columns = BrowserLayoutPolicy.tabGridColumns(widthDp);
        final int cardWidth = dp(Math.min(tvDevice ? 240 : 210,
                Math.max(112, (widthDp - (tvDevice ? 110 : 72)) / columns - 8)));
        final ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(false);
        final GridLayout grid = new GridLayout(this);
        grid.setColumnCount(columns);
        grid.setPadding(dp(8), dp(8), dp(8), dp(8));
        scroller.addView(grid);
        final AlertDialog[] visible = new AlertDialog[1];

        for (int i = 0; i < tabs.size(); i++) {
            final int tabIndex = i;
            BrowserTab tab = tabs.get(i);
            String label = tab.title;
            if (label == null || label.trim().isEmpty()) {
                String url = tab.url;
                String host = UrlTools.isWebUrl(url) ? Uri.parse(url).getHost() : null;
                label = host == null ? "New tab" : host;
            }
            if (label.length() > 48) label = label.substring(0, 48) + "…";
            String subtitle = tab.crashed ? "Renderer crashed" :
                    tab.loading ? "Loading " + tab.progress + "%" :
                    tab.privateMode ? "Private Gecko" : "Browser tab";
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setPadding(dp(4), dp(4), dp(4), dp(4));

            Button select = new Button(this);
            select.setAllCaps(false);
            select.setText((tab == activeTab ? "● " : "○ ") +
                    (tab.privateMode ? "🔒 " : "") + label + "\n" + subtitle);
            select.setTextSize(tvDevice ? 16 : 13);
            select.setMaxLines(3);
            select.setContentDescription("Switch to " +
                    (tab.privateMode ? "private" : "normal") + " tab " + (i + 1) + ": " + label);
            select.setBackgroundDrawable(buttonBackground(tab == activeTab));
            select.setOnClickListener(v -> {
                if (visible[0] != null) visible[0].dismiss();
                switchBrowserTab(tabIndex);
            });
            tile.addView(select, new LinearLayout.LayoutParams(
                    -1, dp(tvDevice ? 102 : 90)));

            Button close = new Button(this);
            close.setAllCaps(false);
            close.setText("× Close");
            close.setTextSize(tvDevice ? 14 : 12);
            close.setContentDescription("Close tab " + (i + 1));
            close.setOnClickListener(v -> {
                if (visible[0] != null) visible[0].dismiss();
                closeBrowserTab(tabIndex);
                showBrowserTabs();
            });
            tile.addView(close, new LinearLayout.LayoutParams(-1,
                    dp(tvDevice ? 44 : 38)));

            GridLayout.LayoutParams cell = new GridLayout.LayoutParams();
            cell.width = cardWidth;
            cell.height = -2;
            cell.setMargins(dp(3), dp(3), dp(3), dp(3));
            grid.addView(tile, cell);
        }

        final Button newTab = new Button(this);
        newTab.setAllCaps(false);
        newTab.setText("+ New tab");
        newTab.setContentDescription("Create new independent browser tab");
        newTab.setOnClickListener(v -> {
            if (visible[0] != null) visible[0].dismiss();
            createBrowserTab(prefs.getString(ENGINE, EnginePolicy.AUTO), null);
        });
        GridLayout.LayoutParams newParams = new GridLayout.LayoutParams();
        newParams.width = cardWidth;
        newParams.height = dp(tvDevice ? 112 : 95);
        newParams.setMargins(dp(3), dp(3), dp(3), dp(3));
        grid.addView(newTab, newParams);

        final Button privateTab = new Button(this);
        privateTab.setAllCaps(false);
        privateTab.setText("+ Private Gecko");
        privateTab.setContentDescription("Create a GeckoView private tab");
        privateTab.setOnClickListener(v -> {
            if (visible[0] != null) visible[0].dismiss();
            if (!createBrowserTab(EnginePolicy.GECKO, null, true))
                toast("Private GeckoView requires Android 8+ and the Gecko APK");
        });
        GridLayout.LayoutParams privateParams = new GridLayout.LayoutParams();
        privateParams.width = cardWidth;
        privateParams.height = dp(tvDevice ? 112 : 95);
        privateParams.setMargins(dp(3), dp(3), dp(3), dp(3));
        grid.addView(privateTab, privateParams);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Browser tabs (" + tabs.size() + ")")
                .setView(scroller)
                .setNegativeButton("Done", null)
                .setNeutralButton("Close all", (ignored, which) ->
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Close all browser tabs?")
                                .setMessage("All current tab sessions, including private tabs, " +
                                        "will be closed. A clean home tab will remain.")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Close all", (x, y) -> {
                                    clearAllTabSessions();
                                    createBrowserTab(prefs.getString(ENGINE, EnginePolicy.AUTO), null);
                                }).show()).create();
        visible[0] = dialog;
        dialog.show();
        // TV starts on a safe tab selection, never the destructive Close all action.
        if (tvDevice && grid.getChildCount() > 0)
            grid.getChildAt(Math.min(Math.max(0, tabs.indexOf(activeTab)),
                    grid.getChildCount() - 1)).requestFocus();
    }

    /** Engine selection applies to all tabs, never implies cookie/session migration. */
    private void selectEngine(String preference, String restoreUrl) {
        if (popupDialog != null) popupDialog.dismiss();
        if (downloadDialog != null) downloadDialog.dismiss();
        cursorMode = false;
        pageLoading = false;
        pageProgress = 0;
        if (cursorOverlay != null) cursorOverlay.setVisibility(View.GONE);
        clearAllTabSessions();
        if (!createBrowserTab(preference, restoreUrl)) {
            engineReason = "No working rendering engine available";
            status.setText(engineReason);
            syncComposeChrome();
            new AlertDialog.Builder(this).setTitle("Browser unavailable")
                    .setMessage("Neither GeckoView, embedded Chromium, nor System WebView " +
                            "could start. Check the device firmware and installed WebView.")
                    .setPositiveButton("OK", null).show();
        }
    }

    /** Readable omnibox: show the complete URL while editing, hostname otherwise. */
    private void showCompactAddress() {
        if (address == null || address.hasFocus() || engine == null) return;
        String current = engine.currentUrl();
        if (!UrlTools.isWebUrl(current)) return;
        String host = Uri.parse(current).getHost();
        if (host != null && !host.isEmpty()) address.setText(host);
    }

    private void submitAddress() {
        String value = address.getText().toString();
        try {
            String url = UrlTools.navigate(value);
            if (url == null) return;
            open(url);
            InputMethodManager input = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (input != null) input.hideSoftInputFromWindow(address.getWindowToken(), 0);
            if (engine != null) engine.view().requestFocus();
        } catch (IllegalArgumentException invalid) {
            toast(invalid.getMessage());
        }
    }

    private void open(String url) {
        if (!UrlTools.isWebUrl(url)) {
            toast("Only valid HTTP/HTTPS websites can be opened");
            return;
        }
        if (BrowserNetworkPolicy.blocksCleartext(url,
                BrowserNetworkPolicy.allowHttpForFlavor(BuildConfig.FLAVOR))) {
            toast("Unencrypted HTTP is blocked in the modern browser. Use HTTPS.");
            return;
        }
        if (engine == null) { toast("Browser engine unavailable"); return; }
        homeRequested = false;
        if (activeTab != null) {
            activeTab.home = false;
            activeTab.url = url;
        }
        showComposeHome(false);
        engine.loadUrl(url);
        address.setText(url);
        if (engine != null) engine.view().requestFocus();
        showCompactAddress();
        syncComposeChrome();
    }

    private void home() {
        homeRequested = true;
        if (activeTab != null) {
            activeTab.home = true;
            activeTab.url = null;
        }
        address.setText("");
        bookmarkButton.setText("☆");
        String html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>" +
                "<style>*{box-sizing:border-box}body{font-family:Arial,sans-serif;background:#0c1524;" +
                "color:#f1f6ff;margin:0;padding:clamp(24px,6vw,72px)}main{max-width:1100px;margin:auto}" +
                "h1{font-size:clamp(30px,5vw,48px);margin:0 0 12px}p{color:#afc0da;font-size:18px;" +
                "line-height:1.55}nav{display:flex;flex-wrap:wrap;gap:12px;margin:28px 0}" +
                "a{color:#f1f6ff;text-decoration:none;display:inline-block;background:#203957;" +
                "border:1px solid #36516f;border-radius:12px;padding:18px 22px;font-size:19px;" +
                "min-width:120px;text-align:center}a:focus{outline:4px solid #58b5ff}" +
                "@media(max-width:420px){nav{display:grid;grid-template-columns:repeat(2,minmax(0,1fr))}" +
                "a{min-width:0;padding:16px 8px;font-size:17px}}</style></head><body><main>" +
                "<h1>Rudy Browser</h1><p>Browse comfortably on your phone, tablet or TV.</p><nav>" +
                "<a href='https://www.google.com/'>Google</a>" +
                "<a href='https://www.youtube.com/'>YouTube</a>" +
                "<a href='https://www.wikipedia.org/'>Wikipedia</a>" +
                "<a href='https://www.bing.com/'>Bing</a></nav>" +
                "<p>Enter a website or search above. The menu has bookmarks, history and settings.</p>" +
                "</main></body></html>";
        if (engine != null) engine.loadHomeHtml(html);
        showComposeHome(true);
        if (engine != null && composeHome == null) engine.view().requestFocus();
    }

    private JSONArray loadList(String key) {
        try { return new JSONArray(prefs.getString(key, "[]")); }
        catch (JSONException invalid) { return new JSONArray(); }
    }

    private void persistList(String key, JSONArray list) {
        prefs.edit().putString(key, list.toString()).apply();
    }

    private JSONObject entry(String url, String title) {
        JSONObject entry = new JSONObject();
        try {
            entry.put("url", url);
            entry.put("title", title == null || title.trim().isEmpty() ? url : title);
        } catch (JSONException ignored) { }
        return entry;
    }

    private boolean isBookmarked(String url) {
        JSONArray bookmarks = loadList(BOOKMARKS);
        for (int i = 0; i < bookmarks.length(); i++) {
            if (url.equals(bookmarks.optJSONObject(i) == null ? "" : bookmarks.optJSONObject(i).optString("url")))
                return true;
        }
        return false;
    }

    private void refreshBookmarkButton() {
        String url = engine == null ? null : engine.currentUrl();
        bookmarkButton.setText(UrlTools.isWebUrl(url) && isBookmarked(url) ? "★" : "☆");
        syncComposeChrome();
    }

    private void toggleBookmark() {
        String url = engine == null ? null : engine.currentUrl();
        if (!UrlTools.isWebUrl(url)) {
            toast("Open a website before bookmarking");
            return;
        }
        JSONArray old = loadList(BOOKMARKS);
        JSONArray next = new JSONArray();
        boolean removed = false;
        for (int i = 0; i < old.length(); i++) {
            JSONObject item = old.optJSONObject(i);
            if (item == null) continue;
            if (url.equals(item.optString("url"))) { removed = true; continue; }
            next.put(item);
        }
        if (!removed) next.put(entry(url, engine.title()));
        persistList(BOOKMARKS, next);
        refreshBookmarkButton();
        toast(removed ? "Bookmark removed" : "Bookmarked");
    }

    private void saveHistory(String url, String title) {
        if (!UrlTools.isWebUrl(url)) return;
        JSONArray old = loadList(HISTORY);
        JSONArray next = new JSONArray();
        next.put(entry(url, title));
        for (int i = 0; i < old.length() && next.length() < 40; i++) {
            JSONObject item = old.optJSONObject(i);
            if (item != null && !url.equals(item.optString("url"))) next.put(item);
        }
        persistList(HISTORY, next);
    }

    private void showSavedList(String key, boolean remove) {
        JSONArray data = loadList(key);
        final List<JSONObject> entries = new ArrayList<JSONObject>();
        final List<String> labels = new ArrayList<String>();
        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.optJSONObject(i);
            if (item == null || !UrlTools.isWebUrl(item.optString("url"))) continue;
            entries.add(item);
            labels.add(item.optString("title", item.optString("url")));
        }
        if (entries.isEmpty()) { toast("No saved pages yet"); return; }
        final String title = remove ? "Remove bookmark"
                : key.equals(BOOKMARKS) ? "Bookmarks" : "Recently visited";
        final String[] options = labels.toArray(new String[0]);
        final DialogInterface.OnClickListener selection = new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        String selected = entries.get(which).optString("url");
                        if (remove) {
                            JSONArray next = new JSONArray();
                            for (JSONObject item : entries) {
                                if (!selected.equals(item.optString("url"))) next.put(item);
                            }
                            persistList(BOOKMARKS, next);
                            refreshBookmarkButton();
                            toast("Bookmark removed");
                        } else open(selected);
                    }
                };
        if (composeChrome != null) {
            composeChrome.showActions(title, options, new BrowserChrome.ChoiceHandler() {
                @Override public void onChoice(int index) { selection.onClick(null, index); }
            });
        } else {
            new AlertDialog.Builder(this).setTitle(title)
                    .setItems(options, selection).setNegativeButton("Cancel", null).show();
        }
    }

    private void updateUserAgent() {
        if (engine != null) engine.setDesktopMode(prefs.getBoolean(DESKTOP, false));
    }

    private void focusAddress() {
        if (composeChrome != null) {
            composeChrome.focusAddress();
            return;
        }
        address.requestFocus();
        address.selectAll();
        InputMethodManager input = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (input != null) input.showSoftInput(address, InputMethodManager.SHOW_IMPLICIT);
    }

    private void showExtensions() {
        if (engine instanceof BrowserExtensions) {
            showRealFirefoxExtensions((BrowserExtensions) engine);
            return;
        }
        new ExtensionUi(this, prefs, engine).show();
    }

    /** Actual Mozilla-installed addons, never the WebView add-on/scriptlet UI. */
    private void showRealFirefoxExtensions(final BrowserExtensions provider) {
        provider.listAddons((addons, error) -> {
            if (engine != provider || isFinishing()) return;
            if (error != null) { toast(error); return; }
            final String[] choices = new String[addons.size() + 2];
            for (int i = 0; i < addons.size(); i++) {
                BrowserExtensions.AddonInfo addon = addons.get(i);
                choices[i] = (addon.enabled ? "● " : "○ ") + addon.name +
                        " · " + addon.version + (addon.builtIn ? " (bundled)" : "");
            }
            choices[addons.size()] = "Install bundled Firefox test extension";
            choices[addons.size() + 1] = "Install Mozilla-signed HTTPS .xpi";
            new AlertDialog.Builder(this)
                    .setTitle("Firefox WebExtensions")
                    .setItems(choices, (dialog, choice) -> {
                        if (engine != provider) return;
                        if (choice < addons.size()) {
                            showFirefoxAddonDetail(provider, addons.get(choice));
                        } else if (choice == addons.size()) {
                            new AlertDialog.Builder(this)
                                    .setTitle("Install bundled extension?")
                                    .setMessage("Rudy Page Marker is an original, local Firefox " +
                                            "WebExtension. It only marks https://example.com " +
                                            "for testing and requests no privileged APIs.")
                                    .setNegativeButton("Cancel", null)
                                    .setPositiveButton("Install", (ignored, which) ->
                                            provider.installBundledTestAddon((ok, msg) -> {
                                                toast(msg);
                                                if (engine == provider) showRealFirefoxExtensions(provider);
                                            })).show();
                        } else {
                            promptSignedFirefoxXpi(provider);
                        }
                    })
                    .setNegativeButton("Close", null).show();
        });
    }

    private void showFirefoxAddonDetail(final BrowserExtensions provider,
                                         final BrowserExtensions.AddonInfo addon) {
        String detail = "ID: " + addon.id + "\nVersion: " + addon.version +
                "\nStatus: " + (addon.enabled ? "Enabled" : "Disabled") +
                "\nAPI permissions: " + addon.requiredPermissions +
                "\nWebsite access: " + addon.requiredOrigins +
                "\nBundled: " + addon.builtIn;
        new AlertDialog.Builder(this).setTitle(addon.name)
                .setMessage(detail)
                .setNegativeButton("Back", (ignored, which) -> showRealFirefoxExtensions(provider))
                .setNeutralButton("Uninstall", (ignored, which) ->
                        new AlertDialog.Builder(this)
                                .setTitle("Uninstall Firefox extension?")
                                .setMessage(addon.name + "\n" + addon.id +
                                        "\nThis also deletes extension-specific data.")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Uninstall", (x, y) ->
                                        provider.uninstallAddon(addon.id, (ok, msg) -> {
                                            toast(msg);
                                            if (engine == provider) showRealFirefoxExtensions(provider);
                                        })).show())
                .setPositiveButton(addon.enabled ? "Disable" : "Enable", (ignored, which) ->
                        provider.setAddonEnabled(addon.id, !addon.enabled, (ok, msg) -> {
                            toast(msg);
                            if (engine == provider) showRealFirefoxExtensions(provider);
                        })).show();
    }

    private void promptSignedFirefoxXpi(final BrowserExtensions provider) {
        final EditText url = new EditText(this);
        url.setSingleLine(true);
        url.setHint("https://trusted.example/addon.xpi");
        url.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_VARIATION_URI);
        new AlertDialog.Builder(this)
                .setTitle("Install signed Firefox XPI")
                .setMessage("Enter a trusted HTTPS .xpi URL. Mozilla will validate the " +
                        "add-on signature and its requested permissions will require " +
                        "separate approval. Chrome .crx files are not supported.")
                .setView(url)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Review source", (ignored, which) -> {
                    final String value = url.getText().toString().trim();
                    if (value.length() > 2048) { toast("Extension URL is too long"); return; }
                    Uri uri = Uri.parse(value);
                    final String host = uri.getHost();
                    if (!"https".equalsIgnoreCase(uri.getScheme()) ||
                            host == null || uri.getPath() == null ||
                            !uri.getPath().toLowerCase(java.util.Locale.ROOT).endsWith(".xpi")) {
                        toast("Direct HTTPS .xpi URL required");
                        return;
                    }
                    new AlertDialog.Builder(this)
                            .setTitle("Trust this extension source?")
                            .setMessage("Domain: " + host + "\nFull URL: " + value +
                                    "\n\nOnly install from a publisher you trust.")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Continue", (x, y) ->
                                    provider.installSignedXpi(value, (ok, msg) -> {
                                        toast(msg);
                                        if (engine == provider) showRealFirefoxExtensions(provider);
                                    })).show();
                }).show();
    }

    private void toggleCursorMode() {
        if (!tvDevice) {
            toast("Remote pointer is only needed on TV devices");
            return;
        }
        if (engine == null) return;
        cursorMode = !cursorMode;
        cursorOverlay.setVisibility(cursorMode ? View.VISIBLE : View.GONE);
        if (cursorMode) {
            cursorOverlay.post(new Runnable() {
                @Override public void run() { cursorOverlay.recenter(); }
            });
            status.setText("Remote pointer: arrows move, OK clicks, Back exits");
        } else {
            status.setText(engine.name() + "  •  D-pad: navigate  •  Menu: options");
        }
        engine.view().requestFocus();
    }

    private void moveCursor(int dx, int dy) {
        int px = dp(44);
        int margin = dp(12);
        cursorOverlay.place(
                CursorPolicy.move(cursorOverlay.cursorX(), dx, px,
                        cursorOverlay.getWidth(), margin),
                CursorPolicy.move(cursorOverlay.cursorY(), dy, px,
                        cursorOverlay.getHeight(), margin));
    }

    private void tapCursor() {
        if (engine == null || cursorOverlay.getWidth() == 0) return;
        View target = engine.view();
        long now = SystemClock.uptimeMillis();
        int x = cursorOverlay.cursorX();
        int y = cursorOverlay.cursorY();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, x, y, 0);
        try {
            target.dispatchTouchEvent(down);
            target.dispatchTouchEvent(up);
        } finally {
            down.recycle();
            up.recycle();
        }
    }

    /** Launches a separate, manually installed Chromium browser without sharing URL or cookies. */
    private void showNativeChromium() {
        if (Build.VERSION.SDK_INT < 29) {
            new AlertDialog.Builder(this).setTitle("Native Chromium unavailable")
                .setMessage("Native Chromium requires Android 10+. Continue browsing with WebView.")
                .setPositiveButton("OK", null).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("Open separate Chromium app?")
            .setMessage("This is an independent browser with its own data and Chrome "
                + "extension manager, not an embedded engine. Your current URL, cookies, "
                + "account sessions and scripts are not transferred. Rudy Browser remains "
                + "available as the WebView fallback if the companion isn't installed.")
            .setNegativeButton("Stay here", null)
            .setPositiveButton("Open", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    final String pkg = "io.rudycity.tvbrowser.chromium";
                    Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
                    if (launch == null && Build.VERSION.SDK_INT >= 21)
                        launch = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
                    if (launch == null) {
                        toast("Native Chromium companion is not installed");
                        return;
                    }
                    try {
                        startActivity(launch);
                    } catch (android.content.ActivityNotFoundException invalid) {
                        toast("Native Chromium could not be launched");
                    } catch (SecurityException denied) {
                        toast("Native Chromium launch was denied");
                    }
                }
            }).show();
    }

    /** Frequent page actions first; advanced settings stay out of the main browser UI. */
    private void showMenu() {
        final String url = engine == null ? null : engine.currentUrl();
        final boolean bookmarked = UrlTools.isWebUrl(url) && isBookmarked(url);
        final String[] options = {
                "Reload page",
                "Home",
                "Forward",
                "Tabs (" + tabs.size() + ")",
                bookmarked ? "Remove bookmark" : "Bookmark this page",
                (engine instanceof BrowserExtensions ? "Firefox WebExtensions" :
                        "WebView extensions (" + new ExtensionSettings(prefs).enabledCount() + " enabled)"),
                "Bookmarks",
                "Recent history",
                "Android downloads",
                "Find in page",
                "More tools and settings"
        };
        final DialogInterface.OnClickListener selection = new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0: if (engine != null) engine.reload(); break;
                            case 1: home(); break;
                            case 2: if (engine != null && engine.canGoForward())
                                        engine.goForward(); break;
                            case 3: showBrowserTabs(); break;
                            case 4: toggleBookmark(); break;
                            case 5: showExtensions(); break;
                            case 6: showSavedList(BOOKMARKS, false); break;
                            case 7: showSavedList(HISTORY, false); break;
                            case 8: showSystemDownloads(); break;
                            case 9: showFindInPage(); break;
                            case 10: showToolsMenu(); break;
                        }
                    }
                };
        if (composeChrome != null) {
            composeChrome.showActions("Rudy Browser", options, new BrowserChrome.ChoiceHandler() {
                @Override public void onChoice(int index) { selection.onClick(null, index); }
            });
        } else {
            new AlertDialog.Builder(this).setTitle("Rudy Browser")
                    .setItems(options, selection).setNegativeButton("Close", null).show();
        }
    }

    private void showSystemDownloads() {
        new AlertDialog.Builder(this)
                .setTitle("Saved downloads")
                .setMessage("System WebView downloads appear in Android Downloads. " +
                        "GeckoView files are saved to the folder you chose in the " +
                        "Android document picker. Rudy Browser does not silently " +
                        "collect files or open downloaded installers.")
                .setNegativeButton("Close", null)
                .setPositiveButton("Open Android Downloads", (ignored, which) -> {
                    try {
                        startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));
                    } catch (android.content.ActivityNotFoundException | SecurityException unavailable) {
                        toast("No downloads app is available on this device");
                    }
                }).show();
    }

    private void showFindInPage() {
        if (engine == null || !UrlTools.isWebUrl(engine.currentUrl())) {
            toast("Open a website before finding page text");
            return;
        }
        final BrowserEngine target = engine;
        final EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Text to find");
        search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        search.setFilters(new android.text.InputFilter[]{
                new android.text.InputFilter.LengthFilter(256)});
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Find in page")
                .setView(search)
                .setNegativeButton("Done", (ignored, which) -> target.clearFindInPage())
                .setNeutralButton("Previous", null)
                .setPositiveButton("Find / Next", null).create();
        // A local action class avoids java.util.function.Consumer, absent
        // from Android 4.4's boot classpath without core-library desugaring.
        final class FindAction {
            void run(boolean backwards) {
                if (engine != target || !UrlTools.isWebUrl(target.currentUrl())) {
                    dialog.dismiss();
                    return;
                }
                String query = search.getText().toString();
                if (query.trim().isEmpty()) {
                    search.setError("Enter text");
                    return;
                }
                if (!target.findInPage(query, backwards, (current, total) ->
                        dialog.setTitle(total == 0 ? "No matches" :
                                "Find in page • " + current + "/" + total)))
                    search.setError("Search not supported by this engine");
            }
        }
        final FindAction find = new FindAction();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v ->
                    find.run(false));
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v ->
                    find.run(true));
            search.requestFocus();
        });
        dialog.setOnDismissListener(ignored -> target.clearFindInPage());
        dialog.show();
    }

    private void showToolsMenu() {
        final boolean desktop = prefs.getBoolean(DESKTOP, false);
        final String mode = prefs.getString(ENGINE, EnginePolicy.AUTO);
        final String[] options = {
                "Enter address or search",
                "Browser engine: " + mode + " (" +
                        (engine == null ? "unavailable" : engine.name()) + ")",
                desktop ? "Desktop mode: ON" : "Desktop mode: OFF",
                "Zoom in",
                "Zoom out",
                "Remove a saved bookmark",
                "HTML5 video playback controls",
                tvDevice ? (cursorMode ? "Remote pointer: ON" : "Remote pointer: OFF")
                        : "Remote pointer (Android TV only)",
                "Native Chromium + Chrome Extensions (separate app)",
                "Clear browsing data",
                "About Rudy Browser",
                "Site permissions for current page",
                "Save page as PDF",
                "Scroll page down",
                "Scroll page up",
                "Memory and inactive tabs",
                prefs.getBoolean(DARK_THEME, true) ?
                        "Modern touch theme: DARK" : "Modern touch theme: LIGHT"
        };
        final DialogInterface.OnClickListener selection = new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0: focusAddress(); break;
                            case 1: chooseEngine(); break;
                            case 2:
                                prefs.edit().putBoolean(DESKTOP, !desktop).apply();
                                updateUserAgent();
                                if (engine != null) engine.reload();
                                toast(desktop ? "Desktop mode disabled" : "Desktop mode enabled");
                                break;
                            case 3: if (engine != null) engine.zoomIn(); break;
                            case 4: if (engine != null) engine.zoomOut(); break;
                            case 5: showSavedList(BOOKMARKS, true); break;
                            case 6: new ExtensionUi(MainActivity.this, prefs, engine)
                                    .showVideoControls(); break;
                            case 7: if (tvDevice) toggleCursorMode();
                                    else toast("Remote pointer is only for Android TV");
                                    break;
                            case 8: showNativeChromium(); break;
                            case 9: confirmClear(); break;
                            case 13:
                                if (engine == null || !engine.scrollPage(true))
                                    toast("Scrolling unavailable");
                                break;
                            case 14:
                                if (engine == null || !engine.scrollPage(false))
                                    toast("Scrolling unavailable");
                                break;
                            case 15: showMemoryAndTabs(); break;
                            case 16:
                                boolean dark = !prefs.getBoolean(DARK_THEME, true);
                                prefs.edit().putBoolean(DARK_THEME, dark).apply();
                                if (composeChrome != null) composeChrome.setDarkTheme(dark);
                                if (composeHome != null) composeHome.setDarkTheme(dark);
                                toast(dark ? "Dark browser chrome enabled" :
                                        "Light browser chrome enabled");
                                break;
                            case 12:
                                if (engine == null || !engine.savePagePdf())
                                    toast("PDF export unavailable for this page or device");
                                break;
                            case 11:
                                if (engine == null || !engine.showSitePermissions())
                                    toast("Site permissions are available for Gecko HTTPS pages");
                                break;
                            case 10: new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("About Rudy Browser")
                                    .setMessage("Version " + BuildConfig.VERSION_NAME + " • Android API " + Build.VERSION.SDK_INT + "\nActive: " +
                                            (engine == null ? "none" : engine.name()) +
                                            "\n" + engineReason +
                                            "\nModern embedded Chromium requires Android 10+ and " +
                                            "a bundled Chromium provider. This build uses WebView " +
                                            "unless that independent engine is provided.")
                                    .setPositiveButton("OK", null).show();
                                    break;
                        }
                    }
                };
        if (composeChrome != null) {
            composeChrome.showActions("Browser tools", options, new BrowserChrome.ChoiceHandler() {
                @Override public void onChoice(int index) { selection.onClick(null, index); }
            });
        } else {
            new AlertDialog.Builder(this).setTitle("Browser tools")
                    .setItems(options, selection)
                    .setNegativeButton("Back", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface dialog, int which) {
                            showMenu();
                        }
                    }).show();
        }
    }

    /** Explicit resource reclamation: never discard unsaved forms silently. */
    private void showMemoryAndTabs() {
        final int inactive = Math.max(0, tabs.size() - (activeTab == null ? 0 : 1));
        String memory = "Unavailable";
        try {
            android.app.ActivityManager manager = (android.app.ActivityManager)
                    getSystemService(Context.ACTIVITY_SERVICE);
            if (manager != null) {
                android.os.Debug.MemoryInfo[] process =
                        manager.getProcessMemoryInfo(new int[]{android.os.Process.myPid()});
                if (process != null && process.length == 1)
                    memory = process[0].getTotalPss() / 1024 + " MiB";
            }
        } catch (RuntimeException unsupported) {
            // No private URLs, process IDs, or crash diagnostics in UI errors.
        }
        String details = "Open tabs: " + tabs.size() +
                "\nInactive tabs: " + inactive +
                "\nApp main-process memory (PSS): " + memory +
                "\n\nFirefox Gecko content subprocesses are not included " +
                "in this single-process figure. Inactive tab renderers stay " +
                "paused to preserve unsaved form state. You may explicitly " +
                "close them to reclaim their memory.";
        final AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                .setTitle("Memory and browser tabs")
                .setMessage(details)
                .setNegativeButton("Back", null);
        if (inactive > 0) dialog.setPositiveButton("Close inactive tabs",
                (ignored, which) -> new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Close " + inactive + " inactive tabs?")
                        .setMessage("Inactive pages and unsaved form input will be lost. " +
                                "The current page and its session remain open.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Close inactive tabs", (d, button) -> {
                            final BrowserTab keep = activeTab;
                            for (int i = tabs.size() - 1; i >= 0; i--) {
                                if (tabs.get(i) != keep) closeBrowserTab(i);
                            }
                            toast("Inactive browser tabs closed");
                        }).show());
        dialog.show();
    }

    private void chooseEngine() {
        final String[] labels = {"Automatic (GeckoView / Chromium / WebView)", "Firefox GeckoView", "Standalone Chromium", "System WebView"};
        final String[] values = {EnginePolicy.AUTO, EnginePolicy.GECKO, EnginePolicy.CHROMIUM, EnginePolicy.WEBVIEW};
        final DialogInterface.OnClickListener selection = new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int index) {
                        final String next = values[index];
                        // A user may switch engines, but private URLs are never
                        // transferred into a normal, persistent renderer.
                        final String url = !BrowserSessionPolicy.canTransferUrl(activeTab != null && activeTab.privateMode)
                                ? null : engine == null ? null : engine.currentUrl();
                        final Runnable apply = () -> {
                            prefs.edit().putString(ENGINE, next).apply();
                            selectEngine(next, url);
                            recoveryAttempts = 0;
                            toast(engineReason);
                        };
                        if (tabs.size() > 1) {
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("Switch rendering engine for all tabs?")
                                    .setMessage("This closes the other " + (tabs.size() - 1) +
                                            " tab sessions. Cookies, history and site permissions " +
                                            "are not transferred between GeckoView and WebView.")
                                    .setNegativeButton("Cancel", null)
                                    .setPositiveButton("Switch and close other tabs",
                                            (ignored, which) -> apply.run()).show();
                        } else {
                            apply.run();
                        }
                    }
                };
        if (composeChrome != null) {
            composeChrome.showActions("Rendering engine", labels, new BrowserChrome.ChoiceHandler() {
                @Override public void onChoice(int index) { selection.onClick(null, index); }
            });
        } else {
            new AlertDialog.Builder(this).setTitle("Rendering engine")
                    .setItems(labels, selection).setNegativeButton("Cancel", null).show();
        }
    }

    private void clearBrowsingDataNow() {
        if (clearingBrowserData) return;
        clearingBrowserData = true;
        // A Gecko runtime owns shared storage; other Gecko tabs will be closed.
        List<BrowserEngine> clearing = new ArrayList<>();
        boolean geckoAdded = false;
        for (BrowserTab tab : tabs) {
            if (tab.browser == null) continue;
            if ("Firefox GeckoView".equals(tab.browser.name())) {
                if (geckoAdded) continue;
                geckoAdded = true;
            }
            clearing.add(tab.browser);
        }
        if (clearing.isEmpty()) { finishClearBrowsingData(false); return; }
        final int[] pending = {clearing.size()};
        final boolean[] completed = {true};
        // Close every other GeckoSession before the Gecko runtime clears storage.
        BrowserEngine geckoOwner = null;
        for (BrowserEngine browser : clearing)
            if ("Firefox GeckoView".equals(browser.name())) geckoOwner = browser;
        if (geckoOwner != null) for (BrowserTab tab : tabs) {
            if (tab.browser != geckoOwner && tab.browser != null &&
                    "Firefox GeckoView".equals(tab.browser.name()))
                tab.browser.destroy();
        }
        status.setText("Clearing browser storage…");
        for (BrowserEngine browser : clearing) {
            browser.clearBrowsingDataWithResult(ok -> runOnUiThread(() -> {
                completed[0] &= ok;
                if (--pending[0] == 0) finishClearBrowsingData(completed[0]);
            }));
        }
    }

    private void finishClearBrowsingData(boolean completed) {
        if (!clearingBrowserData || isFinishing() || isDestroyed()) return;
        prefs.edit().remove(HISTORY).remove(RESTORE_TABS).remove(RESTORE_SELECTED).apply();
        clearAllTabSessions();
        clearingBrowserData = false;
        createBrowserTab(prefs.getString(ENGINE, EnginePolicy.AUTO), null);
        toast(completed ? "Browser clear APIs completed; old tabs closed"
                : "Some browser data could not be cleared; check storage and retry");
    }

    private void confirmClear() {
        final String title = "Clear browsing data?";
        final String detail = "Closes all tabs and requests deletion of browser cache, " +
                "history, cookies and web storage. Waits for renderer callbacks; " +
                "storage erasure cannot be independently guaranteed. Bookmarks stay saved.";
        if (composeChrome != null) {
            composeChrome.showConfirmation(title, detail, "Clear data", new Runnable() {
                @Override public void run() { clearBrowsingDataNow(); }
            });
        } else {
            new AlertDialog.Builder(this).setTitle(title).setMessage(detail)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Clear", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface dialog, int which) {
                            clearBrowsingDataNow();
                        }
                    }).show();
        }
    }

    /** All user-triggered new-window links require an explicit human choice. */
    private void confirmPopup(String openerUrl, final String destinationUrl) {
        if (BrowserNetworkPolicy.blocksCleartext(destinationUrl,
                BrowserNetworkPolicy.allowHttpForFlavor(BuildConfig.FLAVOR))) {
            toast("Blocked an unencrypted HTTP popup");
            return;
        }
        if (!PopupPolicy.isPreviewable(destinationUrl)) {
            toast("Unsafe popup destination blocked");
            return;
        }
        if (popupDialog != null && popupDialog.isShowing()) {
            toast("Another popup was blocked while a decision is pending");
            return;
        }
        final BrowserEngine requestingEngine = engine;
        final boolean privatePopup = BrowserSessionPolicy.popupMustStayPrivate(
                activeTab != null && activeTab.privateMode);
        if (privatePopup && PopupPolicy.usesPlainHttp(destinationUrl)) {
            toast("Private tabs only open HTTPS popups");
            return;
        }
        String opener = PopupPolicy.isPreviewable(openerUrl) ? openerUrl : "Browser home";
        StringBuilder details = new StringBuilder("Opened from:\n").append(opener)
                .append("\n\nDestination domain:\n").append(PopupPolicy.host(destinationUrl))
                .append("\n\nFull destination link:\n").append(destinationUrl)
                .append("\n\nApproved popup links open in a separate tab after human consent.");
        if (PopupPolicy.mightInstallSoftware(destinationUrl))
            details.append("\n\nWARNING: The link looks like an installer/executable.");
        if (PopupPolicy.usesPlainHttp(destinationUrl))
            details.append("\n\nWARNING: The destination does not use HTTPS.");
        details.append("\n\nBlock this popup unless you trust the link.");
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Open this new-tab link?")
                .setMessage(details.toString())
                .setNegativeButton("Block", null)
                .setPositiveButton("Open link", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface ignored, int which) {
                        if (engine != requestingEngine) {
                            toast("Popup expired after switching browser engines");
                            return;
                        }
                        createBrowserTab(privatePopup ? EnginePolicy.GECKO :
                                prefs.getString(ENGINE, EnginePolicy.AUTO),
                                destinationUrl, privatePopup);
                    }
                }).create();
        popupDialog = dialog;
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override public void onDismiss(DialogInterface ignored) {
                if (popupDialog == dialog) popupDialog = null;
            }
        });
        dialog.show();
        // On a TV remote, the initial focus is always the safe choice.
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus();
    }

    /** Prevent drive-by APK and other automatic downloads even after a popup approval. */
    private void download(final String url, final String userAgent,
                          final String contentDisposition, final String mimeType) {
        if (BrowserNetworkPolicy.blocksCleartext(url,
                BrowserNetworkPolicy.allowHttpForFlavor(BuildConfig.FLAVOR))) {
            toast("HTTP downloads are blocked in the modern browser");
            return;
        }
        if (!PopupPolicy.isPreviewable(url)) {
            toast("Unsafe download URL blocked");
            return;
        }
        if (downloadDialog != null && downloadDialog.isShowing()) {
            toast("Another download was blocked while a decision is pending");
            return;
        }
        String guessed = URLUtil.guessFileName(url, contentDisposition, mimeType);
        String cleaned = guessed == null ? "download.bin"
                : guessed.replaceAll("[^A-Za-z0-9._-]", "_");
        final String filename = cleaned.isEmpty() || cleaned.equals(".")
                || cleaned.equals("..") ? "download.bin" : cleaned;
        StringBuilder details = new StringBuilder("File:\n").append(filename)
                .append("\n\nSource link:\n").append(url);
        if (PopupPolicy.mightInstallSoftware(url) ||
                PopupPolicy.mightInstallSoftware("https://example.invalid/" + filename) ||
                (mimeType != null && mimeType.toLowerCase(java.util.Locale.US)
                        .contains("android.package-archive")))
            details.append("\n\nWARNING: Possible installer. Never install untrusted APKs.");
        if (PopupPolicy.usesPlainHttp(url))
            details.append("\n\nWARNING: Download source is not HTTPS.");
        details.append("\n\nNothing downloads without your approval.");
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Allow file download?")
                .setMessage(details.toString())
                .setNegativeButton("Block", null)
                .setPositiveButton("Download", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface ignored, int which) {
                        enqueueDownload(url, userAgent, mimeType, filename);
                    }
                }).create();
        downloadDialog = dialog;
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override public void onDismiss(DialogInterface ignored) {
                if (downloadDialog == dialog) downloadDialog = null;
            }
        });
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus();
    }

    private void enqueueDownload(String url, String userAgent, String mimeType, String name) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle(name);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            if (mimeType != null) request.setMimeType(mimeType);
            if (userAgent != null) request.addRequestHeader("User-Agent", userAgent);
            String cookie = engine == null ? null : engine.cookieHeader(url);
            if (cookie != null && !cookie.isEmpty()) request.addRequestHeader("Cookie", cookie);
            request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, name);
            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager == null) { toast("Download service not available"); return; }
            manager.enqueue(request);
            toast("Download started: " + name);
        } catch (Exception error) {
            toast("Download failed: " + error.getClass().getSimpleName());
        }
    }

    private void setGeckoFullscreen(boolean fullscreen) {
        if (geckoFullscreen == fullscreen) return;
        geckoFullscreen = fullscreen;
        if (toolbarStack != null) toolbarStack.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        if (status != null) status.setVisibility(fullscreen ? View.GONE
                : (tvDevice ? View.VISIBLE : View.GONE));
        if (fullscreen && loadingProgress != null) loadingProgress.setVisibility(View.GONE);
        updateSystemBars();
        if (!fullscreen) updateJavaProgress();
    }

    private void hideFullscreen() {
        if (geckoFullscreen) {
            if (engine != null) engine.exitFullscreen();
            setGeckoFullscreen(false);
        }
        if (fullscreenView == null) return;
        View old = fullscreenView;
        Runnable closeAction = fullscreenClose;
        fullscreenView = null;
        fullscreenClose = null;
        root.removeView(old);
        if (closeAction != null) closeAction.run();
        if (engine != null) engine.view().requestFocus();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && engine != null) {
            int key = event.getKeyCode();
            if (key == KeyEvent.KEYCODE_MENU) { showMenu(); return true; }
            if (key == KeyEvent.KEYCODE_SEARCH) { focusAddress(); return true; }
            // PageUp/PageDown are explicit remote/keyboard navigation commands;
            // never steal arrow keys from web forms, video or the system keyboard.
            if (tvDevice && fullscreenView == null && !geckoFullscreen &&
                    !cursorMode && engine.view().hasFocus() &&
                    (key == KeyEvent.KEYCODE_PAGE_DOWN || key == KeyEvent.KEYCODE_PAGE_UP)) {
                if (engine.scrollPage(key == KeyEvent.KEYCODE_PAGE_DOWN)) return true;
            }
            if (tvDevice && cursorMode && fullscreenView == null && !geckoFullscreen
                    && engine.view().hasFocus()) {
                switch (key) {
                    case KeyEvent.KEYCODE_DPAD_LEFT: moveCursor(-1, 0); return true;
                    case KeyEvent.KEYCODE_DPAD_RIGHT: moveCursor(1, 0); return true;
                    case KeyEvent.KEYCODE_DPAD_UP: moveCursor(0, -1); return true;
                    case KeyEvent.KEYCODE_DPAD_DOWN: moveCursor(0, 1); return true;
                    case KeyEvent.KEYCODE_DPAD_CENTER:
                    case KeyEvent.KEYCODE_ENTER: tapCursor(); return true;
                }
            }
            // Disabled helper leaves WebView to handle its own D-pad events.
            boolean tvHelperAllowed = engine instanceof WebViewEngine
                    && new ExtensionSettings(prefs).enabledForUrl(
                            ExtensionPolicy.TV_NAVIGATION, engine.currentUrl());
            if (tvDevice && fullscreenView == null && engine.view().hasFocus() && tvHelperAllowed) {
                String command = null;
                switch (key) {
                    case KeyEvent.KEYCODE_DPAD_LEFT: command = "move(-1,0)"; break;
                    case KeyEvent.KEYCODE_DPAD_RIGHT: command = "move(1,0)"; break;
                    case KeyEvent.KEYCODE_DPAD_UP: command = "move(0,-1)"; break;
                    case KeyEvent.KEYCODE_DPAD_DOWN: command = "move(0,1)"; break;
                    case KeyEvent.KEYCODE_DPAD_CENTER:
                    case KeyEvent.KEYCODE_ENTER: command = "select()"; break;
                }
                if (command != null) {
                    engine.executeDpadCommand(command);
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override public void onBackPressed() {
        if (fullscreenView != null || geckoFullscreen) hideFullscreen();
        else if (cursorMode) toggleCursorMode();
        else if (engine != null && engine.canGoBack()) engine.goBack();
        else super.onBackPressed();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        for (BrowserTab tab : new ArrayList<>(tabs)) {
            if (tab.browser != null &&
                    tab.browser.onActivityResult(requestCode, resultCode, data))
                return;
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                                     int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Results belong to the original tab/session, never to the current tab by assumption.
        for (BrowserTab tab : new ArrayList<>(tabs)) {
            if (tab.browser != null &&
                    tab.browser.onAndroidPermissionsResult(requestCode, permissions, grantResults))
                return;
        }
    }

    @Override protected void onPause() {
        if (engine != null) engine.onPause();
        super.onPause();
    }
    @Override protected void onResume() {
        super.onResume();
        if (engine != null) engine.onResume();
    }
    @Override protected void onDestroy() {
        if (popupDialog != null) popupDialog.dismiss();
        if (downloadDialog != null) downloadDialog.dismiss();
        hideFullscreen();
        clearAllTabSessions();
        super.onDestroy();
    }
}
