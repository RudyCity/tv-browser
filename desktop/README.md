# Rudy Browser Desktop (Rust + Tauri 2)

The desktop application is built with **Rust and Tauri 2**. It is a separate
desktop target and does not replace the existing Android application or change
its package IDs.

## Native browser architecture

The desktop app uses Tauri's native multi-webview API in one native window:

- The local `main-ui` webview renders Rudy Browser's tab strip, toolbar, home
  workspace, data managers and settings.
- Each tab owns a native content webview (`browser-content-<id>`) that renders
  websites with the operating system's webview engine: WebView2 on Windows,
  WebKitGTK on Linux, and WKWebView on macOS.
- Only the active tab's content webview is visible. Rust keeps every tab
  webview at the same content bounds and hides the inactive ones.
- The toolbar webview measures its own chrome height and reports it to Rust,
  which converts the native window's physical dimensions and scale factor into
  logical content bounds. Resize and scale-factor events reapply those bounds.
- The local toolbar and every remote page are separate webviews. Only `main-ui`
  receives the capability to call browser Rust commands; a remote page webview
  receives no Tauri IPC capability. This is a capability boundary, not a claim
  that the browser profile is private or isolated from other sites.
- Browser Back and Forward call the page's History API. Rust keeps a separate
  URL-based navigation stack per tab and refreshes it on page-load events and
  periodic URL checks, which also observes many same-document history changes.
  A new tab starts with its own empty stack. This lightweight stack cannot
  distinguish every duplicate-URL or `replaceState` history entry exactly.

Tauri's multi-webview API is currently behind its `unstable` Cargo feature.
The Rust and CLI versions are pinned in this repository because this layout
depends on that API.

## Current desktop behavior

- Tabs: a tab strip with one content webview per tab, `Ctrl+T` for a new tab,
  `Ctrl+Shift+N` for a private tab, `Ctrl+W` to close, `Ctrl+Shift+T` to reopen
  the most recently closed tab, and `Ctrl+Tab` or `Ctrl+Shift+Tab` to cycle.
  Tabs can be reordered by dragging, and `ArrowLeft`, `ArrowRight`, `Home` and
  `End` move between tabs while the strip has keyboard focus. Closing the last
  tab opens a fresh blank tab.
- Session restore: on launch the app reopens the tabs from the previous session
  with their page titles and zoom, and selects the tab that was active. Private
  tabs are never stored.
- Address/search bar with HTTPS by default for domain input. Only HTTP and
  HTTPS external navigation is allowed, plus the internal `about:blank`
  initialization page.
- Back, forward, reload, home and a connection indicator that reports whether
  the current page is HTTPS or plain HTTP.
- Page zoom with a visible percentage, `Ctrl+=` to zoom in, `Ctrl+-` to zoom
  out and `Ctrl+0` to reset. Zoom is applied by the native webview, not by
  injecting styles into the page.
- Find in page with `Ctrl+F`, match counting, next/previous match and `Escape`
  to close.
- Dark and light themes. The palette follows `DESIGN.md`; the theme is stored in
  the local UI profile and defaults to dark.
- A 2px progress bar driven by native page-load events, and a local error view
  when an address is rejected, cannot be opened, or finishes as the webview's
  own failure document. The error view keeps the failed address and offers a
  retry action.
- Bookmarks are stored in the local browser UI profile, can be added or removed
  from the toolbar, and are available from the home dashboard and searchable
  bookmarks manager. `Ctrl+D` toggles the current page; `Ctrl+B` opens the
  manager.
- A searchable history manager records completed top-level page visits, keeps
  visit counts and titles, and can be cleared independently. `Ctrl+H` opens it.
- A download manager lists every download with live progress, byte counts and
  status (running, paused, completed, failed, cancelled), and offers pause,
  resume, cancel and open actions. Finished and unfinished transfers are stored
  in the local UI profile, so the list survives a restart; transfers that were
  still running become `interrupted` on the next start. `Ctrl+J` opens it.
- Home shows recent pages and saved bookmarks. `Escape` closes a data manager,
  the settings view or the find bar.
- Downloads are saved to the operating system's Downloads directory, with a
  temporary-directory fallback if that path cannot be resolved. Filenames are
  sanitized and collisions receive a numeric suffix. A paused download resumes
  with an HTTP range request and falls back to a fresh transfer when the server
  ignores the range.
- An HTTP(S) `window.open` request is redirected into the tab that requested it,
  which becomes the active tab. This is a single-view fallback, not a new tab or
  popup window. Other popup URL schemes are denied.
- Settings cover the language, theme, search engine, home address, the default
  zoom, site permissions and local data clearing.
- The interface ships in English and Indonesian. The selected language applies
  immediately to the toolbar, data managers, settings, status messages and
  dialogs, and is stored with the other settings.
- Site permissions are an explicit allow-list per origin. A permission request
  is granted only when the requesting origin and the permission kind are both
  listed in Settings. A denied request raises a notice with an allow action, and
  the next request from that origin and kind is granted.
- Clearing cookies and site data, history, or download activity is available
  from Settings and from the data managers.

Private tabs use the platform's incognito mode (WebView2 InPrivate on Windows,
a non-persistent data store on macOS, WebKitGTK's ephemeral session on Linux).
Private tabs do not write history, download activity or session entries.
Downloads started from a private tab still write a file to disk, and every
private tab shares the same incognito session.

Not implemented in this desktop target: tab tear-off, multiple windows, split
view, extension or userscript support, ad and tracker filtering, resuming an
interrupted download after a restart, synchronized browser data, an in-app
updater, and code signing.

An interrupted transfer keeps its partial file on disk and is listed as
`interrupted` after a restart; it is not resumed automatically, because the
native download task does not survive the process.

A failed load is detected by inspecting the finished document for the webview's
error page. A page that never finishes loading keeps the progress bar until the
next navigation, because the native load events report no failure status.

Ad and tracker filtering is not implemented. Tauri stores a web resource request
hook, but neither `tauri-runtime-wry` nor `wry` invokes it for HTTP(S) requests
in this stack, so a filter would need a local proxy or a different engine
integration. The permission prompt is also limited: the native permission
callback is synchronous and reports only the permission kind, so a request is
denied first and the notice with the allow action follows, instead of blocking
the request while the user decides.

## Platform support and feature limits

| Capability | Windows | Linux | macOS | Current status |
|---|---|---|---|---|
| Native page engine | WebView2 | WebKitGTK 4.1 | WKWebView | Uses the OS webview; engine version/behavior follows the installed OS runtime |
| Installer artifact | NSIS and MSI | AppImage and DEB | DMG | Built by the platform CI matrix |
| Tabs | Supported | Supported | Supported | One native webview per tab; the active tab is the only visible content view |
| Private tabs | Supported (InPrivate, WebView2 runtime 101+) | Supported | Supported | Incognito session per private tab; no history, downloads list or session entries |
| Session restore | Supported | Supported | Supported | Tabs and the active tab index are stored in the local UI profile |
| Page zoom | Supported | Supported | Supported | Native webview zoom with a visible percentage |
| Find in page | Supported | Supported | Supported | Counts text occurrences and steps through matches with `window.find`; it does not highlight every match |
| Per-origin site permissions | Allow-list | Allow-list | Allow-list | Requests are denied unless the origin and kind are listed in Settings; a denied request raises a notice with an allow action, and there is no blocking prompt |
| Downloads | Supported | Supported | Supported | Managed transfer with progress, pause, resume, cancel and open; finished and interrupted transfers survive a restart, and paused transfers resume with an HTTP range request |
| Language | English, Indonesian | English, Indonesian | English, Indonesian | Interface copy switches immediately and is stored with the other settings |
| Bookmarks and history | Supported | Supported | Supported | Searchable local managers; stored in the local UI webview profile |
| Clear browsing data | Supported | Supported | Supported | Cookies and site data for the shared profile, plus history and download activity |
| Extensions / WebExtensions | Not implemented | Not implemented | Not implemented | No extension runtime, store or install flow |
| Ad/tracker filtering | Not implemented | Not implemented | Not implemented | No filter-list engine or blocking rules shipped |
| Multi-window / popup windows | Not implemented | Not implemented | Not implemented | HTTP(S) popup targets open in the tab that requested them |
| Code signing | Not configured in CI | Not configured in CI | Not configured in CI | Current macOS CI artifact is unsigned; no production signing identity is configured |
| Notarization | Not applicable | Not applicable | Not configured | macOS public distribution still requires Apple Developer ID signing and notarization |
| In-app updater | Not implemented | Not implemented | Not implemented | No updater plugin, update endpoint or signed update feed is configured |

The matrix describes the current source and CI configuration, not a statement
that a feature is impossible on a platform. These items need design, security
review, platform-specific implementation and tests before being advertised as
shipped.

## Prerequisites

- Bun 1.4 or newer. It installs dependencies and runs every JavaScript script.
- Rust stable toolchain.
- Windows: Microsoft C++ Build Tools and WebView2 runtime.
- Linux: GTK3, WebKitGTK 4.1 and the Tauri build dependencies.
- macOS: Xcode Command Line Tools.

## Development

From the repository root:

    cd desktop
    bun install
    bun run tauri -- icon ../art/rudy_browser_mark.svg
    bun run tauri -- dev

The icon set is generated from `art/rudy_browser_mark.svg`; it is not committed.
`bun install` writes `bun.lock`, which pins the resolved dependency graph. CI
installs with `bun install --frozen-lockfile`, so a dependency change without a
lockfile update fails the build.

## Build installers

    cd desktop
    bun install
    bun run tauri -- icon ../art/rudy_browser_mark.svg
    bun run tauri -- build --bundles nsis,msi
    bun run tauri -- build --bundles appimage,deb
    bun run tauri -- build --bundles dmg

Build on the target operating system or use the GitHub Actions desktop matrix.
Artifacts are emitted under `src-tauri/target/release/bundle/`:

| Platform | Installer |
|---|---|
| Windows | NSIS .exe and MSI |
| Linux | AppImage and .deb |
| macOS | DMG containing the .app bundle |

The MSI bundle runs WiX ICE validation, which needs the Windows Installer
service. On a restricted session that service can be unavailable, and the MSI
step then fails with `LGHT0217` or `LGHT0216` while the NSIS installer in the
same run still succeeds. Build the MSI on a normal interactive Windows session.

The Windows NSIS installer is built in `currentUser` mode, so it installs per
user and does not request elevation. NSIS extracts its plugins into `%TEMP%`
while it runs, so a machine whose temporary folder is missing, read only, or
blocked by endpoint protection fails with "Error writing temporary file".
If the workspace or build output directory carries a Windows `Low Mandatory Level`
label, binaries built inside it also inherit Low Integrity, causing the NSIS
installer to fail writing to Medium-integrity `%TEMP%` and causing
`rudy-browser-desktop.exe` to exit immediately with `0xC0000142`. Restoring
`Medium` integrity on the build directory resolves both; otherwise, copying
`target/release/rudy-browser-desktop.exe` to a Medium-integrity folder runs it
portably with only the WebView2 runtime required.

The desktop workflow generates the native icon set from
`art/rudy_browser_mark.svg`, installs frontend dependencies with Bun, then runs
the JavaScript tests, Rust formatting checks, Rust tests, Clippy with warnings
denied, a frontend production build and native installer builds for Windows,
Linux and macOS. Every successful platform job uploads its installer artifact
to the GitHub Actions run.

A commit to `main` whose message begins with `release(desktop):` also publishes
a GitHub **pre-release** tagged `desktop-v<version>` after all three platform
jobs succeed. The release includes the Windows EXE/MSI, Linux AppImage/DEB,
macOS DMG and a `SHA256SUMS.txt` file. Ordinary pushes, pull requests and
manual workflow runs do not publish a release.

The current GitHub Actions jobs may be blocked before runner startup by account
billing or spending-limit restrictions. A workflow being queued or rejected
before its first step is **not** evidence that source tests or builds passed.

## Keyboard shortcuts

| Shortcut | Action |
|---|---|
| `Ctrl+T` / `Ctrl+Shift+N` | New tab / new private tab |
| `Ctrl+W` / `Ctrl+Shift+T` | Close tab / reopen the last closed tab |
| `Ctrl+Tab` / `Ctrl+Shift+Tab` | Next tab / previous tab |
| `ArrowLeft`, `ArrowRight`, `Home`, `End` | Move between tabs when the strip has focus |
| `Ctrl+L` | Focus the address bar |
| `Ctrl+F` | Find in page (`Enter` next, `Shift+Enter` previous, `Escape` close) |
| `Ctrl+D` / `Ctrl+B` / `Ctrl+H` / `Ctrl+J` | Bookmark page / bookmarks / history / downloads |
| `Ctrl+,` | Settings |
| `Ctrl+=` / `Ctrl+-` / `Ctrl+0` | Zoom in / zoom out / reset zoom |
| `Ctrl+R`, `Alt+Left`, `Alt+Right` | Reload, back, forward |
| `F11` | Toggle fullscreen |

## Release readiness

The current macOS CI build is unsigned. Public distribution requires Apple
Developer ID code signing and notarization. Windows and Linux signing are also
not configured in the current workflow. A production release additionally
needs a maintained update/signing strategy, tested installation and upgrade
paths, and real-device/OS-version validation.
