# TV Browser built-in extensions

## Scope and compatibility

Version 0.4.0 ships a **built-in WebView add-on manager**, not Chrome extension
runtime support. Android System WebView does not implement Chrome's extensions
APIs, service workers, Manifest V3 permission system or CRX loader. Nothing in
this release downloads an extension package from the Chrome Web Store.

The standalone Chromium provider remains an optional future integration.
Switching renderers does not magically port WebView add-ons.

## Add-on behavior

| Add-on | Default | Scope |
| --- | --- | --- |
| Ad & tracker blocker | Enabled | WebView third-party requests, limited static hostname list |
| Dark mode | Disabled | WebView HTTP(S) pages, CSS at document-end |
| TV navigation | Enabled | WebView D-pad navigation |
| Userscripts | Disabled | WebView HTTPS exact hostname, local JavaScript at document-end |
| HTML5 video controls | Enabled | WebView page HTML5 videos; whitelist-only player actions |

## Settings and permissions

- All settings use local Android SharedPreferences named tv_browser.
- Each add-on has a global switch and per-HTTP(S)-hostname inherit/allow/deny policy.
- Global disable always wins over a site-specific allow. No wildcard domain policies.
- Userscripts additionally require HTTPS. No file, content, data or HTTP execution.
- Script size limit: 32,768 UTF-8 bytes. Store manually with an explicit warning.
- The app never downloads arbitrary extension code.
- Clearing history/cache does not delete saved add-ons and scripts.

## Security and limitations

- Ad blocker does not implement filter-list subscriptions, cosmetic selectors or
  network-wide filtering; interception is confined to WebView resource requests.
- API 19–20: the old request-intercept callback has no reliable main-frame indicator,
  so request interception is intentionally disabled to avoid breaking navigation.
- The Extensions menu shows blocked requests for the active page, with a one-click
  site exception. The reset action removes global switches, site exceptions and all
  saved scripts after confirmation; it preserves regular browser data.
- Dark mode and scripts run after page load rather than at document-start.
- Userscripts run in the website's JavaScript context, without extension sandbox
  isolation, so only use scripts you trust.
- HTML5 video controls operate only on visible video elements in the page DOM;
  embedded third-party iframe players, native players and DRM behavior are not guaranteed.
- Add-ons currently run in the System WebView renderer only. No Chromium .crx,
  Chrome Web Store or Manifest V3 execution. No real-device TV QA yet.

## Manual QA checklist

1. Test API 19, 25 and 29+ TV remote navigation to the Ext button.
2. Toggle each add-on; restart app and confirm persistence.
   Check tracker dashboard reset on navigation and API 19–20 unsupported status.
3. Test exact-host site deny and global-disable precedence; confirm that
   credentials in URL authority and trailing-dot normalization never leak permissions.
4. Confirm no userscript execution on HTTP, local or home pages.
5. Save, enable, execute and delete a harmless HTTPS userscript.
6. Verify third-party tracker blocking but first-party/main-frame bypass.
7. Disable TV helper and verify native D-pad handling.
8. Confirm CRX menu reports unsupported functionality.
9. Verify reset restores default switches and deletes scripts but keeps bookmarks.
10. Check fullscreen video, bookmarks, downloads and engine switching.
