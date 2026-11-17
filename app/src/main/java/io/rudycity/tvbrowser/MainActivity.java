package io.rudycity.tvbrowser;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
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
import android.webkit.URLUtil;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Responsive Android phone, tablet, and TV shell with explicit engine fallback. */
public final class MainActivity extends Activity {
    private static final String PREFERENCES = "tv_browser";
    private static final String BOOKMARKS = "bookmarks";
    private static final String HISTORY = "history";
    private static final String DESKTOP = "desktop_mode";
    private static final String ENGINE = "render_engine";
    private static final int TOOLBAR_BG = Color.rgb(18, 29, 45);
    private static final int HIGHLIGHT = Color.rgb(42, 105, 184);

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
    private FrameLayout engineSlot;
    private String engineReason;
    private EditText address;
    private TextView status;
    private Button bookmarkButton;
    private SharedPreferences prefs;
    private View fullscreenView;
    private Runnable fullscreenClose;
    private int recoveryAttempts;
    private AlertDialog popupDialog;
    private AlertDialog downloadDialog;
    private TvCursorOverlay cursorOverlay;
    private boolean cursorMode;


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
        else if (UrlTools.isWebUrl(restored)) open(restored);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && UrlTools.isWebUrl(intent.getDataString())) open(intent.getDataString());
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        if (engine != null && UrlTools.isWebUrl(engine.currentUrl()))
            outState.putString("current_url", engine.currentUrl());
        super.onSaveInstanceState(outState);
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
        if (tvDevice) getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable buttonBackground(boolean selected) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(selected ? HIGHLIGHT : Color.rgb(31, 47, 68));
        d.setCornerRadius(dp(9));
        d.setStroke(dp(selected ? 2 : 1), selected ? Color.WHITE : Color.rgb(61, 79, 101));
        return d;
    }

    private Button addButton(LinearLayout toolbar, String text, String description,
                             View.OnClickListener action) {
        final Button b = new Button(this);
        b.setText(text);
        b.setContentDescription(description);
        b.setTextSize(18);
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
        toolbarStack.setOrientation(LinearLayout.VERTICAL);
        toolbarStack.setBackgroundColor(TOOLBAR_BG);
        toolbarStack.setPadding(dp(4), dp(4), dp(4), dp(4));
        layout.addView(toolbarStack, new LinearLayout.LayoutParams(-1, -2));
        primaryRow = new LinearLayout(this);
        primaryRow.setGravity(Gravity.CENTER_VERTICAL);
        secondaryRow = new LinearLayout(this);
        secondaryRow.setGravity(Gravity.CENTER_VERTICAL);
        toolbarStack.addView(primaryRow, new LinearLayout.LayoutParams(-1, dp(56)));
        toolbarStack.addView(secondaryRow, new LinearLayout.LayoutParams(-1, dp(56)));

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
        address.setHint("Search or enter URL");
        address.setTextSize(16);
        address.setTextColor(Color.WHITE);
        address.setHintTextColor(Color.rgb(166, 180, 195));
        address.setSelectAllOnFocus(true);
        address.setPadding(dp(12), 0, dp(10), 0);
        address.setBackgroundDrawable(buttonBackground(false));
        primaryRow.addView(address);
        address.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean hasFocus) {
                address.setBackgroundDrawable(buttonBackground(hasFocus));
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

        engineSlot = new FrameLayout(this);
        engineSlot.setBackgroundColor(Color.rgb(12, 20, 30));
        cursorOverlay = new TvCursorOverlay(this);
        engineSlot.addView(cursorOverlay, new FrameLayout.LayoutParams(-1, -1));
        layout.addView(engineSlot, new LinearLayout.LayoutParams(-1, 0, 1));

        status = new TextView(this);
        status.setTextColor(Color.rgb(181, 196, 214));
        status.setBackgroundColor(TOOLBAR_BG);
        status.setPadding(dp(12), dp(4), dp(12), dp(4));
        status.setSingleLine(true);
        status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        status.setTextSize(12);
        status.setText(tvDevice ? "D-pad: select • OK: open • Menu: options"
                : "Swipe to browse • Menu: options");
        layout.addView(status, new LinearLayout.LayoutParams(-1, dp(26)));
        setContentView(root);
    }

    private void addToolbarButton(LinearLayout row, Button button, int sizeDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp));
        params.setMargins(dp(2), dp(2), dp(2), dp(2));
        row.addView(button, params);
    }

    private void addAddressBar(LinearLayout row, int heightDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(heightDp), 1);
        params.setMargins(dp(4), dp(2), dp(4), dp(2));
        row.addView(address, params);
    }

    /** Reparents existing controls without recreating the WebView or losing page history. */
    private void arrangeToolbar() {
        if (primaryRow == null || secondaryRow == null) return;
        int widthDp = getResources().getConfiguration().screenWidthDp;
        if (widthDp <= 0) widthDp = (int) (getResources().getDisplayMetrics().widthPixels /
                getResources().getDisplayMetrics().density);
        boolean compact = BrowserLayoutPolicy.isCompact(widthDp);
        int sizeDp = compact ? 48 : 52;
        primaryRow.removeAllViews();
        secondaryRow.removeAllViews();
        addToolbarButton(primaryRow, backButton, sizeDp);
        addToolbarButton(primaryRow, forwardButton, sizeDp);
        if (!compact) {
            addToolbarButton(primaryRow, homeButton, sizeDp);
            addToolbarButton(primaryRow, reloadButton, sizeDp);
        }
        addAddressBar(primaryRow, sizeDp);
        addToolbarButton(primaryRow, goButton, sizeDp);
        if (compact) {
            addToolbarButton(secondaryRow, homeButton, sizeDp);
            addToolbarButton(secondaryRow, reloadButton, sizeDp);
        }
        LinearLayout actions = compact ? secondaryRow : primaryRow;
        addToolbarButton(actions, bookmarkButton, sizeDp);
        addToolbarButton(actions, extensionsButton, sizeDp);
        if (tvDevice && (!compact || widthDp >= 360))
            addToolbarButton(actions, pointerButton, sizeDp);
        addToolbarButton(actions, menuButton, sizeDp);
        secondaryRow.setVisibility(compact ? View.VISIBLE : View.GONE);
    }

    private final BrowserEngine.Listener rendererEvents = new BrowserEngine.Listener() {
        @Override public void onPageStarted(String url) {
            status.setText("Loading…");
        }
        @Override public void onPageFinished(String url, String title) {
            if (UrlTools.isWebUrl(url)) {
                address.setText(url);
                saveHistory(url, title);
            }
            refreshBookmarkButton();
            status.setText(cursorMode ? "Remote pointer: arrows move, OK clicks, Back exits"
                    : engine.name() + (tvDevice ? "  •  D-pad: navigate  •  Menu: options" :
                    "  •  Swipe to browse  •  Menu: options"));
        }
        @Override public void onProgress(int progress) {
            if (progress < 100) status.setText("Loading " + progress + "%  •  " + engine.name());
        }
        @Override public void onError(String message) {
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
        @Override public void onDownloadRequested(String url, String agent, String disposition, String mimeType) {
            download(url, agent, disposition, mimeType);
        }
        @Override public void onPopupRequested(String openerUrl, String destinationUrl) {
            confirmPopup(openerUrl, destinationUrl);
        }
        @Override public void onEngineCrashed(String reason) {
            if (++recoveryAttempts > 1) {
                status.setText(reason + " — recovery stopped to avoid a crash loop");
                return;
            }
            status.setText(reason + " — switching to WebView…");
            String remembered = null;
            try { if (engine != null) remembered = engine.currentUrl(); }
            catch (RuntimeException ignored) { /* crash recovery still proceeds */ }
            final String url = remembered;
            engineSlot.post(new Runnable() {
                @Override public void run() { selectEngine(EnginePolicy.WEBVIEW, url); }
            });
        }
    };

    private void setUpBrowser() {
        selectEngine(prefs.getString(ENGINE, EnginePolicy.AUTO), null);
    }

    private void selectEngine(String preference, String restoreUrl) {
        if (popupDialog != null) popupDialog.dismiss();
        if (downloadDialog != null) downloadDialog.dismiss();
        cursorMode = false;
        if (cursorOverlay != null) cursorOverlay.setVisibility(View.GONE);
        hideFullscreen();
        BrowserEngine previous = engine;
        engine = null;
        if (previous != null) {
            engineSlot.removeView(previous.view());
            previous.destroy();
        }
        try {
            EngineFactory.Selection selected = EngineFactory.create(this, preference,
                    prefs.getBoolean(DESKTOP, false), rendererEvents);
            engine = selected.engine;
            engineReason = selected.reason;
            // Renderer belongs behind the non-interactive pointer overlay.
            engineSlot.addView(engine.view(), 0, new FrameLayout.LayoutParams(-1, -1));
            engine.view().requestFocus();
            status.setText(engine.name() + "  •  " + engineReason);
            if (restoreUrl != null && UrlTools.isWebUrl(restoreUrl)) open(restoreUrl);
            else home();
        } catch (RuntimeException | LinkageError error) {
            engineReason = "No working rendering engine available";
            status.setText(engineReason);
            new AlertDialog.Builder(this).setTitle("Browser unavailable")
                    .setMessage("Neither the embedded Chromium nor the system WebView could start. " +
                            "Check the TV firmware and system WebView package.")
                    .setPositiveButton("OK", null).show();
        }
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
        if (engine == null) { toast("Browser engine unavailable"); return; }
        engine.loadUrl(url);
        address.setText(url);
        if (engine != null) engine.view().requestFocus();
    }

    private void home() {
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
        if (engine != null) engine.view().requestFocus();
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
        new AlertDialog.Builder(this)
                .setTitle(remove ? "Remove bookmark" : key.equals(BOOKMARKS) ? "Bookmarks" : "Recently visited")
                .setItems(labels.toArray(new String[0]), new DialogInterface.OnClickListener() {
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
                }).setNegativeButton("Cancel", null).show();
    }

    private void updateUserAgent() {
        if (engine != null) engine.setDesktopMode(prefs.getBoolean(DESKTOP, false));
    }

    private void focusAddress() {
        address.requestFocus();
        address.selectAll();
        InputMethodManager input = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (input != null) input.showSoftInput(address, InputMethodManager.SHOW_IMPLICIT);
    }

    private void showExtensions() {
        new ExtensionUi(this, prefs, engine).show();
    }

    private void toggleCursorMode() {
        if (!tvDevice) {
            toast("Remote pointer is only needed on TV devices");
            return;
        }
        if (!(engine instanceof WebViewEngine)) {
            toast("Remote pointer requires System WebView");
            return;
        }
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
        if (!(engine instanceof WebViewEngine) || cursorOverlay.getWidth() == 0) return;
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

    private void showMenu() {
        final boolean desktop = prefs.getBoolean(DESKTOP, false);
        final String mode = prefs.getString(ENGINE, EnginePolicy.AUTO);
        final String[] menu = {"Enter address or search", "Bookmarks", "Recent history", "Remove bookmark",
                "Engine: " + mode + " (" + (engine == null ? "unavailable" : engine.name()) + ")",
                desktop ? "Desktop mode: ON" : "Desktop mode: OFF", "Zoom in", "Zoom out",
                "Clear cache and history", "About", "Extensions (" +
                     new ExtensionSettings(prefs).enabledCount() + " enabled)",
                 cursorMode ? "Remote pointer: ON" : "Remote pointer: OFF",
                 "HTML5 video playback controls"};
        new AlertDialog.Builder(this).setTitle("Rudy Browser")
                .setItems(menu, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0: focusAddress(); break;
                            case 1: showSavedList(BOOKMARKS, false); break;
                            case 2: showSavedList(HISTORY, false); break;
                            case 3: showSavedList(BOOKMARKS, true); break;
                            case 4: chooseEngine(); break;
                            case 5:
                                prefs.edit().putBoolean(DESKTOP, !desktop).apply();
                                updateUserAgent();
                                if (engine != null) engine.reload();
                                toast(desktop ? "Desktop mode disabled" : "Desktop mode enabled");
                                break;
                            case 6: if (engine != null) engine.zoomIn(); break;
                            case 7: if (engine != null) engine.zoomOut(); break;
                            case 8: confirmClear(); break;
                            case 9: new AlertDialog.Builder(MainActivity.this).setTitle("About Rudy Browser")
                                    .setMessage("Version 0.5.0 • Android 4.4+\nActive: " +
                                            (engine == null ? "none" : engine.name()) +
                                            "\n" + engineReason +
                                            "\nModern embedded Chromium requires Android 10+ and " +
                                            "a bundled Chromium provider. Legacy TV devices use WebView. " +
                                            "An old WebView is not a modern Chromium engine.")
                                    .setPositiveButton("OK", null).show(); break;
                            case 10: showExtensions(); break;
                            case 11: toggleCursorMode(); break;
                            case 12: new ExtensionUi(MainActivity.this, prefs, engine)
                                    .showVideoControls(); break;
                        }
                    }
                }).show();
    }

    private void chooseEngine() {
        final String[] labels = {"Automatic (prefer embedded Chromium)", "Standalone Chromium", "System WebView"};
        final String[] values = {EnginePolicy.AUTO, EnginePolicy.CHROMIUM, EnginePolicy.WEBVIEW};
        new AlertDialog.Builder(this).setTitle("Rendering engine")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int index) {
                        String next = values[index];
                        String url = engine == null ? null : engine.currentUrl();
                        prefs.edit().putString(ENGINE, next).apply();
                        selectEngine(next, url);
                        recoveryAttempts = 0;
                        toast(engineReason);
                    }
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void confirmClear() {
        new AlertDialog.Builder(this).setTitle("Clear browsing data?")
                .setMessage("Deletes page cache, history, cookies and web storage. Bookmarks stay saved.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        if (engine != null) engine.clearBrowsingData();
                        prefs.edit().remove(HISTORY).apply();
                        toast("Browsing data cleared");
                        home();
                    }
                }).show();
    }

    /** All user-triggered new-window links require an explicit human choice. */
    private void confirmPopup(String openerUrl, final String destinationUrl) {
        if (!PopupPolicy.isPreviewable(destinationUrl)) {
            toast("Unsafe popup destination blocked");
            return;
        }
        if (popupDialog != null && popupDialog.isShowing()) {
            toast("Another popup was blocked while a decision is pending");
            return;
        }
        final BrowserEngine requestingEngine = engine;
        String opener = PopupPolicy.isPreviewable(openerUrl) ? openerUrl : "Browser home";
        StringBuilder details = new StringBuilder("Opened from:\n").append(opener)
                .append("\n\nDestination domain:\n").append(PopupPolicy.host(destinationUrl))
                .append("\n\nFull destination link:\n").append(destinationUrl)
                .append("\n\nApproved popup links open in the current tab.");
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
                        open(destinationUrl);
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

    private void hideFullscreen() {
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
            if (tvDevice && cursorMode && fullscreenView == null && engine instanceof WebViewEngine
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
            boolean tvHelperAllowed = !(engine instanceof WebViewEngine)
                    || new ExtensionSettings(prefs).enabledForUrl(
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
        if (fullscreenView != null) hideFullscreen();
        else if (cursorMode) toggleCursorMode();
        else if (engine != null && engine.canGoBack()) engine.goBack();
        else super.onBackPressed();
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
        if (engine != null) {
            BrowserEngine old = engine;
            engine = null;
            old.destroy();
        }
        super.onDestroy();
    }
}
