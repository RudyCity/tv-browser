package io.rudycity.tvbrowser;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.KeyEvent;
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

/** Remote-first shell supporting an optional embedded Chromium renderer and legacy WebView. */
public final class MainActivity extends Activity {
    private static final String PREFERENCES = "tv_browser";
    private static final String BOOKMARKS = "bookmarks";
    private static final String HISTORY = "history";
    private static final String DESKTOP = "desktop_mode";
    private static final String ENGINE = "render_engine";
    private static final int TOOLBAR_BG = Color.rgb(18, 29, 45);
    private static final int HIGHLIGHT = Color.rgb(42, 105, 184);

    private FrameLayout root;
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


    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        prefs = getSharedPreferences(PREFERENCES, MODE_PRIVATE);
        setUpUi();
        setUpBrowser();
        String launch = getIntent() == null ? null : getIntent().getDataString();
        if (launch != null && UrlTools.isWebUrl(launch)) open(launch);
        else home();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && UrlTools.isWebUrl(intent.getDataString())) open(intent.getDataString());
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

    private Button addButton(LinearLayout toolbar, String text, String description, View.OnClickListener action) {
        final Button b = new Button(this);
        b.setText(text);
        b.setContentDescription(description);
        b.setTextSize(19);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setPadding(0, 0, 0, 0);
        b.setMinimumWidth(0);
        b.setMinimumHeight(0);
        b.setBackgroundDrawable(buttonBackground(false));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(52), dp(52));
        params.setMargins(dp(2), dp(2), dp(2), dp(2));
        toolbar.addView(b, params);
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

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setBackgroundColor(TOOLBAR_BG);
        toolbar.setPadding(dp(5), dp(5), dp(5), dp(5));
        layout.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(64)));

        addButton(toolbar, "‹", "Back", new View.OnClickListener() {
            @Override public void onClick(View v) { if (engine != null && engine.canGoBack()) engine.goBack(); }
        });
        addButton(toolbar, "›", "Forward", new View.OnClickListener() {
            @Override public void onClick(View v) { if (engine != null && engine.canGoForward()) engine.goForward(); }
        });
        addButton(toolbar, "⌂", "Home", new View.OnClickListener() {
            @Override public void onClick(View v) { home(); }
        });
        addButton(toolbar, "↻", "Refresh", new View.OnClickListener() {
            @Override public void onClick(View v) { if (engine != null) engine.reload(); }
        });

        address = new EditText(this);
        address.setSingleLine(true);
        address.setHint("Search or enter URL");
        address.setTextSize(16);
        address.setTextColor(Color.WHITE);
        address.setHintTextColor(Color.rgb(166, 180, 195));
        address.setSelectAllOnFocus(true);
        address.setPadding(dp(14), 0, dp(12), 0);
        address.setBackgroundDrawable(buttonBackground(false));
        LinearLayout.LayoutParams urlParams = new LinearLayout.LayoutParams(0, dp(52), 1);
        urlParams.setMargins(dp(5), dp(2), dp(4), dp(2));
        toolbar.addView(address, urlParams);
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
        addButton(toolbar, "Go", "Navigate to entered address", new View.OnClickListener() {
            @Override public void onClick(View v) { submitAddress(); }
        });
        bookmarkButton = addButton(toolbar, "☆", "Toggle bookmark", new View.OnClickListener() {
            @Override public void onClick(View v) { toggleBookmark(); }
        });
        addButton(toolbar, "Ext", "Extensions manager", new View.OnClickListener() {
            @Override public void onClick(View v) { showExtensions(); }
        });
        addButton(toolbar, "⋮", "Browser menu", new View.OnClickListener() {
            @Override public void onClick(View v) { showMenu(); }
        });

        engineSlot = new FrameLayout(this);
        engineSlot.setBackgroundColor(Color.rgb(12, 20, 30));
        layout.addView(engineSlot, new LinearLayout.LayoutParams(-1, 0, 1));

        status = new TextView(this);
        status.setTextColor(Color.rgb(181, 196, 214));
        status.setBackgroundColor(TOOLBAR_BG);
        status.setPadding(dp(16), dp(4), dp(16), dp(4));
        status.setTextSize(12);
        status.setText("D-pad: choose link  •  OK: open  •  Menu: options");
        layout.addView(status, new LinearLayout.LayoutParams(-1, dp(25)));
        setContentView(root);
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
            status.setText(engine.name() + "  •  D-pad: navigate  •  Menu: options");
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
            engineSlot.addView(engine.view(), new FrameLayout.LayoutParams(-1, -1));
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
                "<style>body{font-family:Arial,sans-serif;background:#0c1524;color:#f1f6ff;margin:6% 8%}" +
                "h1{font-size:38px;margin-bottom:10px}p{color:#afc0da;font-size:20px}" +
                "a{color:#f1f6ff;text-decoration:none;display:inline-block;background:#203957;" +
                "border-radius:15px;padding:20px 30px;margin:15px 14px 5px 0;font-size:23px}" +
                "a:focus{outline:4px solid #58b5ff}</style></head><body>" +
                "<h1>TV Browser</h1><p>Browse the web with your remote. Press Menu for bookmarks and settings.</p>" +
                "<a href='https://www.google.com/'>Google</a>" +
                "<a href='https://www.youtube.com/'>YouTube</a>" +
                "<a href='https://www.wikipedia.org/'>Wikipedia</a>" +
                "<a href='https://www.bing.com/'>Bing</a>" +
                "<p>Use the address bar above to enter any website or search text.</p></body></html>";
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

    private void showMenu() {
        final boolean desktop = prefs.getBoolean(DESKTOP, false);
        final String mode = prefs.getString(ENGINE, EnginePolicy.AUTO);
        final String[] menu = {"Enter address or search", "Bookmarks", "Recent history", "Remove bookmark",
                "Engine: " + mode + " (" + (engine == null ? "unavailable" : engine.name()) + ")",
                desktop ? "Desktop mode: ON" : "Desktop mode: OFF", "Zoom in", "Zoom out",
                "Clear cache and history", "About", "Extensions (" +
                     new ExtensionSettings(prefs).enabledCount() + " enabled)"};
        new AlertDialog.Builder(this).setTitle("TV Browser")
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
                            case 9: new AlertDialog.Builder(MainActivity.this).setTitle("About TV Browser")
                                    .setMessage("Version 0.3.1 • Android 4.4+\nActive: " +
                                            (engine == null ? "none" : engine.name()) +
                                            "\n" + engineReason +
                                            "\nModern embedded Chromium requires Android 10+ and " +
                                            "a bundled Chromium provider. Legacy TV devices use WebView. " +
                                            "An old WebView is not a modern Chromium engine.")
                                    .setPositiveButton("OK", null).show(); break;
                            case 10: showExtensions(); break;
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

    private void download(String url, String userAgent, String contentDisposition, String mimeType) {
        if (!UrlTools.isWebUrl(url)) { toast("Unsupported download URL"); return; }
        try {
            String name = URLUtil.guessFileName(url, contentDisposition, mimeType);
            name = name.replaceAll("[^A-Za-z0-9._-]", "_");
            if (name.length() == 0 || name.equals(".") || name.equals("..")) name = "download.bin";
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
            // Disabled helper leaves WebView to handle its own D-pad events.
            boolean tvHelperAllowed = !(engine instanceof WebViewEngine)
                    || new ExtensionSettings(prefs).enabledForUrl(
                            ExtensionPolicy.TV_NAVIGATION, engine.currentUrl());
            if (fullscreenView == null && engine.view().hasFocus() && tvHelperAllowed) {
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
        hideFullscreen();
        if (engine != null) {
            BrowserEngine old = engine;
            engine = null;
            old.destroy();
        }
        super.onDestroy();
    }
}
