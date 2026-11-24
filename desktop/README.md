# Rudy Browser Desktop — Rust + Tauri

The desktop application is built with **Rust and Tauri 2**. It is a separate
desktop target and does not replace the existing Android application or change
its package IDs.

## Native browser architecture

The desktop app uses Tauri's native multi-webview API in one native window:

- The local main-ui webview renders Rudy Browser's toolbar and home screen.
- The browser-content webview renders the requested website using the operating
  system's native webview engine: WebView2 on Windows, WebKitGTK on Linux, and
  WKWebView on macOS.
- The browser content view is positioned below the toolbar and above the status
  bar. Rust updates its bounds when the desktop window is resized.
- Site content is isolated from the local toolbar. Only main-ui receives the
  capability to call the browser's Rust commands; the remote content webview
  receives no Tauri IPC capability.

Tauri's multi-webview API is currently behind its unstable Cargo feature.
The Rust and CLI versions are pinned together in this repository because this
desktop layout depends on that API.

## Current desktop features

- Native OS webview rendering.
- Address/search bar, back, forward, reload and home.
- Standard Ctrl/Cmd+L, Ctrl/Cmd+R and Alt+Arrow navigation shortcuts.
- HTTPS by default for domain input; HTTP and HTTPS navigation only.
- Downloads saved to the operating system's Downloads directory.
- Popups and site permissions are denied by default.

This is the initial desktop foundation. Multi-tab management, bookmarks,
history UI, extensions, synchronized data and Android-specific integrations
are not yet implemented in the desktop target.

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
    npm run tauri -- dev

## Build installers

    cd desktop
    npm install
    npm run tauri -- build --bundles nsis,msi
    npm run tauri -- build --bundles appimage,deb
    npm run tauri -- build --bundles dmg

Build on the target operating system or use the GitHub Actions desktop matrix.
Artifacts are emitted under src-tauri/target/release/bundle/:

| Platform | Installer |
|---|---|
| Windows | NSIS .exe and MSI |
| Linux | AppImage and .deb |
| macOS | DMG containing the .app bundle |

The macOS CI build is unsigned. Public distribution requires Apple Developer
ID code signing and notarization.
