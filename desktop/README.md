# Rudy Browser Desktop — Rust + Tauri 2

The desktop application is built with **Rust and Tauri 2**. It is a separate
desktop target and does not replace the existing Android application or change
its package IDs.

## Native browser architecture

The desktop app uses Tauri's native multi-webview API in one native window:

- The local `main-ui` webview renders Rudy Browser's toolbar and home screen.
- The `browser-content` webview renders websites using the operating system's
  native webview engine: WebView2 on Windows, WebKitGTK on Linux, and WKWebView
  on macOS.
- Rust sizes both child webviews from the native window's physical dimensions
  and scale factor. Native resize and scale-factor events reapply their logical
  bounds, including after maximize/restore and display-DPI changes.
- The local toolbar and remote page are separate webviews. Only `main-ui`
  receives the capability to call browser Rust commands; remote page content
  receives no Tauri IPC capability. This is a capability boundary, not a claim
  that the browser profile is private or isolated from other sites.
- Browser Back and Forward call the page's History API. Rust maintains a
  URL-based navigation stack and refreshes it on page-load events and periodic
  URL checks, which also observes many same-document history changes. This
  lightweight stack cannot distinguish every duplicate-URL or
  `replaceState` history entry exactly.

Tauri's multi-webview API is currently behind its `unstable` Cargo feature.
The Rust and CLI versions are pinned in this repository because this layout
depends on that API.

## Current desktop behavior

- Address/search bar, URL-stack-based Back/Forward, reload and home.
- HTTPS by default for domain input; only HTTP and HTTPS external navigation
  are allowed (plus the internal `about:blank` initialization page).
- Downloads are accepted automatically and saved to the operating system's
  Downloads directory, with a temporary-directory fallback if that path cannot
  be resolved. Filenames are sanitized and collisions receive a numeric suffix.
- An HTTP(S) `window.open` request is redirected into the existing browser
  content view. This is a single-view fallback, not a new tab or popup window.
  Other popup URL schemes are denied.
- Web permission requests are denied. There is no per-origin permission prompt
  or user-configurable grant UI.

Multi-tab management, bookmarks, a visible history manager, a download manager,
and synchronized browser data are not implemented in this desktop target.

## Platform support and feature limits

| Capability | Windows | Linux | macOS | Current status |
|---|---|---|---|---|
| Native page engine | WebView2 | WebKitGTK 4.1 | WKWebView | Uses the OS webview; engine version/behavior follows the installed OS runtime |
| Installer artifact | NSIS and MSI | AppImage and DEB | DMG | Built by the platform CI matrix |
| Separate private profile / incognito | Not implemented | Not implemented | Not implemented | One browser-content webview; no private-profile lifecycle or profile selector |
| Per-origin site permissions | Not implemented | Not implemented | Not implemented | Permission requests are denied; no grant/remember UI |
| Downloads | Supported, basic | Supported, basic | Supported, basic | Auto-save to Downloads (temporary fallback); no prompt, progress manager, pause or cancel UI |
| Extensions / WebExtensions | Not implemented | Not implemented | Not implemented | No extension runtime, store or install flow |
| Ad/tracker filtering | Not implemented | Not implemented | Not implemented | No filter-list engine or blocking rules shipped |
| Code signing | Not configured in CI | Not configured in CI | Not configured in CI | Current macOS CI artifact is unsigned; no production signing identity is configured |
| Notarization | Not applicable | Not applicable | Not configured | macOS public distribution still requires Apple Developer ID signing and notarization |
| In-app updater | Not implemented | Not implemented | Not implemented | No updater plugin, update endpoint or signed update feed is configured |
| Multi-tab / popup windows | Not implemented | Not implemented | Not implemented | HTTP(S) popup targets reuse the current content view |

The matrix describes the current source and CI configuration, not a statement
that a feature is impossible on a platform. These items need design, security
review, platform-specific implementation and tests before being advertised as
shipped.

## Prerequisites

- Node.js 20.19+ and npm.
- Rust stable toolchain.
- Windows: Microsoft C++ Build Tools and WebView2 runtime.
- Linux: GTK3, WebKitGTK 4.1 and the Tauri build dependencies.
- macOS: Xcode Command Line Tools.

## Development

From the repository root:

    cd desktop
    npm install
    npm run tauri -- icon ../art/rudy_browser_mark.svg
    npm run tauri -- dev

## Build installers

    cd desktop
    npm install
    npm run tauri -- icon ../art/rudy_browser_mark.svg
    npm run tauri -- build --bundles nsis,msi
    npm run tauri -- build --bundles appimage,deb
    npm run tauri -- build --bundles dmg

Build on the target operating system or use the GitHub Actions desktop matrix.
Artifacts are emitted under `src-tauri/target/release/bundle/`:

| Platform | Installer |
|---|---|
| Windows | NSIS .exe and MSI |
| Linux | AppImage and .deb |
| macOS | DMG containing the .app bundle |

The desktop workflow generates the native icon set from `art/rudy_browser_mark.svg`, then runs JavaScript tests, Rust formatting checks, Rust tests,
Clippy with warnings denied, a frontend production build and native installer
builds for Windows, Linux and macOS. It uploads per-platform artifacts to the
GitHub Actions run; it does not publish a GitHub Release.

The current GitHub Actions jobs may be blocked before runner startup by account
billing or spending-limit restrictions. A workflow being queued or rejected
before its first step is **not** evidence that source tests or builds passed.

## Release readiness

The current macOS CI build is unsigned. Public distribution requires Apple
Developer ID code signing and notarization. Windows and Linux signing are also
not configured in the current workflow. A production release additionally
needs a maintained update/signing strategy, tested installation and upgrade
paths, and real-device/OS-version validation.
