# Rudy Browser Desktop

Desktop companion for Rudy Browser on Windows, Linux and macOS. This is a separate desktop target; it does not replace the Android app or change Android application IDs.

## Desktop engine

The desktop browser uses Electron's bundled Chromium engine. The webview guest is enabled explicitly by the Electron main process and runs with Node integration disabled, context isolation, sandboxing, web security and popups disabled. Navigation is restricted to HTTP(S) and the initial blank page. Permission requests default to deny. Downloads pause until the user chooses a save destination in the native dialog.

This provides a real browser renderer rather than a decorative shell. Android-only integrations (GeckoView extensions, Android runtime permissions, native Chromium companion and TV remote handling) are not claimed as available on desktop.

## Prerequisites

- Node.js 20+ and npm.
- Linux: GTK3, NSS and standard Electron runtime libraries.
- macOS: macOS 11+ recommended for current Electron builds.
- Windows: Chromium is bundled by Electron; no separate WebView runtime is required.

## Development

From the repository root:

```sh
cd desktop
npm install
npm start
```

## Build installers

```sh
cd desktop
npm run dist -- --win nsis portable
npm run dist -- --linux AppImage deb
npm run dist -- --mac dmg zip
```

Build on the target OS or a compatible CI runner. Outputs are under desktop/release/:

- Windows: NSIS installer EXE and portable EXE.
- Linux: AppImage and Debian package.
- macOS: DMG and ZIP containing the app bundle.

GitHub Actions builds all three OS targets independently and uploads artifacts. macOS builds are unsigned; public distribution requires Apple Developer ID code signing and notarization. The app currently provides a single browsing surface; tabs, sync and parity with Android extensions are future desktop work.
