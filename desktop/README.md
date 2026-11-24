# Rudy Browser Desktop

Desktop companion for Rudy Browser on Windows, Linux and macOS. It is a separate desktop target and does not replace the Android application, alter Android package IDs, or claim to reuse Android WebView/GeckoView.

## Architecture

The initial desktop shell uses Tauri 2 and the OS native webview: WebView2 on Windows, WebKitGTK on Linux, and WKWebView on macOS. It is not bundled Chromium or GeckoView. Android-only features are not advertised as desktop-supported until native desktop equivalents are implemented and tested.

## Prerequisites

- Node.js 20+ and Rust stable
- Windows: Visual Studio C++ Build Tools and WebView2 Runtime
- Linux: WebKitGTK 4.1, GTK3, appindicator and build dependencies
- macOS: Xcode Command Line Tools

## Development

From repository root:

```sh
cd desktop
npm install
npm run tauri dev
```

## Build

```sh
cd desktop
npm run tauri build
```

Bundles are written to `desktop/src-tauri/target/release/bundle/`. CI creates Windows NSIS + MSI, Linux AppImage + Debian package, and macOS DMG + app bundle. Public macOS distribution still requires Apple Developer signing and notarization.
