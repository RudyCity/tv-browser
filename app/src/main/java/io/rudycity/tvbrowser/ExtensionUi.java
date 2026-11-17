package io.rudycity.tvbrowser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;

/**
 * Remote-accessible built-in add-on manager. This is deliberately NOT a Chrome .crx runtime.
 * WebView supports the four first-party add-ons; the standalone Chromium provider requires
 * its own integration before claiming these add-ons work in that renderer.
 */
public final class ExtensionUi {
    private final Activity activity;
    private final BrowserEngine engine;
    private final ExtensionSettings settings;

    public ExtensionUi(Activity activity, SharedPreferences preferences, BrowserEngine engine) {
        this.activity = activity;
        this.engine = engine;
        this.settings = new ExtensionSettings(preferences);
    }

    private void info(String title, String detail) {
        new AlertDialog.Builder(activity).setTitle(title).setMessage(detail)
                .setPositiveButton("OK", null).show();
    }

    private String pageUrl() {
        if (engine == null) return null;
        try { return engine.currentUrl(); }
        catch (RuntimeException unavailable) { return null; }
    }

    private void changed() {
        if (engine != null) {
            try { engine.reload(); }
            catch (RuntimeException unavailable) {
                Toast.makeText(activity, "Reload the page to apply add-on changes", Toast.LENGTH_LONG).show();
            }
        }
    }

    private boolean supportedRenderer() {
        return engine instanceof WebViewEngine;
    }

    /** Entry point from the toolbar, browser menu and TV remote. */
    public void show() {
        final String[] names = new String[9];
        for (int i = 0; i < ExtensionPolicy.IDS.length; i++) {
            String id = ExtensionPolicy.IDS[i];
            names[i] = ExtensionPolicy.NAMES[i] + (settings.enabled(id) ? "  •  ON" : "  •  OFF");
        }
        String site = ExtensionPolicy.host(pageUrl());
        names[4] = "Permissions for " + (site == null ? "current website" : site);
        names[5] = "Edit userscript for current HTTPS website";
        names[6] = "Tracker protection status";
        names[7] = "Reset add-ons and saved scripts";
        names[8] = "Chrome Web Store / .crx compatibility";
        // A list dialog must not also setMessage: some Android TV versions hide its items.
        new AlertDialog.Builder(activity)
                .setTitle(supportedRenderer() ? "Extensions - WebView" : "Extensions - WebView only")
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int item) {
                        if (item < ExtensionPolicy.IDS.length) showDetails(item);
                        else if (item == 4) showSitePermissions();
                        else if (item == 5) confirmUserscriptEditor();
                        else if (item == 6) showTrackerStatus();
                        else if (item == 7) confirmResetAll();
                        else info("Chrome extensions not available",
                                "Chrome Web Store / .crx files are not supported by Android WebView. "
                                + "The standalone Chromium embedder and the required extension APIs "
                                + "are not integrated. No extension is downloaded or installed.");
                    }
                }).setNegativeButton("Close", null).show();
    }

    private void showTrackerStatus() {
        final String page = pageUrl();
        final String host = ExtensionPolicy.host(page);
        int total = supportedRenderer() ? ((WebViewEngine) engine).blockedTrackerRequests() : 0;
        boolean active = host != null && supportedRenderer()
                && settings.enabledForUrl(ExtensionPolicy.AD_BLOCKER, page)
                && android.os.Build.VERSION.SDK_INT >= 21;
        String text = host == null ? "Open an HTTP/HTTPS website to view protection for that site."
                : "Site: " + host + "\n"
                    + "Protection: " + (active ? "On" : "Off or unavailable") + "\n"
                    + "Blocked subresource requests this page: " + total + "\n\n"
                    + "These numbers cover only a small built-in tracker list. "
                    + "Older Android 4.4 / API 19-20 disables interception to protect top-level navigation.";
        AlertDialog.Builder dialog = new AlertDialog.Builder(activity)
                .setTitle("Tracker protection")
                .setMessage(text)
                .setNegativeButton("Back", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int button) { show(); }
                });
        if (host != null) {
            dialog.setPositiveButton(active ? "Disable for site" : "Site permissions",
                    new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int button) {
                            if (supportedRenderer() && settings.enabled(ExtensionPolicy.AD_BLOCKER)
                                    && android.os.Build.VERSION.SDK_INT >= 21
                                    && settings.enabledForUrl(ExtensionPolicy.AD_BLOCKER, page)) {
                                settings.setSiteRule(ExtensionPolicy.AD_BLOCKER, host,
                                        ExtensionSettings.DENY);
                                changed();
                                showTrackerStatus();
                            } else showSiteRule(0);
                        }
                    });
        }
        dialog.show();
    }

    private void confirmResetAll() {
        new AlertDialog.Builder(activity).setTitle("Reset all add-ons?")
                .setMessage("Removes all per-site permissions and "
                        + settings.savedScriptCount() + " saved userscript(s), "
                        + "then restores built-in add-on defaults. "
                        + "Bookmarks, engine choice and history are not deleted. "
                        + "This action cannot be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Reset add-ons", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int button) {
                        settings.resetAll();
                        changed();
                        show();
                    }
                }).show();
    }

    private void showDetails(final int index) {
        final String id = ExtensionPolicy.IDS[index];
        final String host = ExtensionPolicy.host(pageUrl());
        String details = ExtensionPolicy.DESCRIPTIONS[index] + "\n\n"
                + "Global: " + (settings.enabled(id) ? "Enabled" : "Disabled") + "\n"
                + "Site: " + (host == null ? "Open a website to set its permissions"
                    : host + " (" + settings.siteRule(id, host) + ")") + "\n"
                + "Renderer: " + (supportedRenderer() ? "System WebView" : "Unavailable");
        AlertDialog.Builder dialog = new AlertDialog.Builder(activity)
                .setTitle(ExtensionPolicy.NAMES[index])
                .setMessage(details)
                .setPositiveButton(settings.enabled(id) ? "Disable" : "Enable",
                        new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int button) {
                                settings.setEnabled(id, !settings.enabled(id));
                                changed();
                                show();
                            }
                        })
                .setNegativeButton("Back", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int button) { show(); }
                });
        if (host != null) {
            dialog.setNeutralButton("Site access", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int button) {
                    showSiteRule(index);
                }
            });
        }
        dialog.show();
    }

    private void showSitePermissions() {
        final String host = ExtensionPolicy.host(pageUrl());
        if (host == null) {
            info("Site permissions", "Open an HTTP or HTTPS website first.");
            return;
        }
        final String[] options = new String[ExtensionPolicy.IDS.length];
        for (int i = 0; i < options.length; i++) {
            String id = ExtensionPolicy.IDS[i];
            options[i] = ExtensionPolicy.NAMES[i] + "  •  " + settings.siteRule(id, host);
        }
        new AlertDialog.Builder(activity).setTitle("Permissions: " + host)
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int index) {
                        showSiteRule(index);
                    }
                })
                .setNegativeButton("Back", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int button) { show(); }
                }).show();
    }

    private void showSiteRule(final int index) {
        final String host = ExtensionPolicy.host(pageUrl());
        if (host == null) { show(); return; }
        final String id = ExtensionPolicy.IDS[index];
        final String[] rules = {
                ExtensionSettings.INHERIT, ExtensionSettings.ALLOW, ExtensionSettings.DENY
        };
        String current = settings.siteRule(id, host);
        int selected = ExtensionSettings.ALLOW.equals(current) ? 1
                : ExtensionSettings.DENY.equals(current) ? 2 : 0;
        new AlertDialog.Builder(activity).setTitle(ExtensionPolicy.NAMES[index] + " on " + host)
                .setSingleChoiceItems(new String[] {
                    "Use global setting",
                    "Allow when globally enabled",
                    "Block on this site"
                }, selected, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int selection) {
                        d.dismiss();
                        settings.setSiteRule(id, host, rules[selection]);
                        changed();
                        showSitePermissions();
                    }
                })
                .setNegativeButton("Back", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int button) {
                        showSitePermissions();
                    }
                }).show();
    }

    private void confirmUserscriptEditor() {
        final String url = pageUrl();
        if (!ExtensionPolicy.userscriptOrigin(url)) {
            info("Userscripts", "Navigate to an HTTPS website before adding a userscript. "
                    + "HTTP, file, data and home pages are not eligible.");
            return;
        }
        final String host = ExtensionPolicy.host(url);
        new AlertDialog.Builder(activity).setTitle("Script safety: " + host)
                .setMessage("Only paste JavaScript you wrote or trust. It runs inside the page "
                        + "and can read and change website content. Scripts are stored locally "
                        + "for this exact hostname and run after page load only when the global "
                        + "Userscript manager is enabled and this site is not blocked.")
                .setPositiveButton("I understand", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int button) {
                        editUserscript(host);
                    }
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void editUserscript(final String host) {
        final EditText editor = new EditText(activity);
        editor.setSingleLine(false);
        editor.setMinLines(7);
        editor.setMaxLines(12);
        editor.setGravity(Gravity.TOP);
        editor.setTextSize(14);
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editor.setHint("// JavaScript for " + host);
        editor.setText(settings.savedScript(host));
        editor.setSelectAllOnFocus(false);
        new AlertDialog.Builder(activity).setTitle("Userscript: " + host)
                .setMessage("Runs at document-end on HTTPS, exact hostname only. "
                        + "Maximum 32 KiB. Leave empty to remove.")
                .setView(editor)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int button) {
                        String script = editor.getText().toString();
                        if (script.getBytes(StandardCharsets.UTF_8).length > ExtensionSettings.MAX_SCRIPT_BYTES) {
                            info("Script too long", "Userscripts are limited to 32 KiB.");
                            return;
                        }
                        settings.setScript(host, script);
                        changed();
                        Toast.makeText(activity, script.trim().isEmpty()
                                ? "Userscript removed" : "Userscript saved", Toast.LENGTH_LONG).show();
                    }
                })
                .setNeutralButton("Delete", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int button) {
                        settings.setScript(host, "");
                        changed();
                        Toast.makeText(activity, "Userscript removed", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null).show();
    }
}
