# Rudy Browser — Android phone, tablet and TV browser

## Development 0.8.1 Beta — Edge-to-edge UI, Rudy Design System v2 & Flagship Modals

[Rudy Browser 0.8.1 Beta](https://github.com/RudyCity/tv-browser) incorporates the overhauled Rudy Design System v2, modern edge-to-edge system bar insets (`.navigationBarsPadding()`), dynamic segmented Tab Manager, Unsplash-powered custom Home atmosphere with Speed Dial, and modernized dialogs.

VersionName 0.8.1 / versionCode 13; no native Chromium AAR is bundled.

## Download / GitHub Pre-release (18 November 2026)

**[Rudy Browser v0.7.1 Development Preview](https://github.com/RudyCity/tv-browser/releases/tag/v0.7.1-preview.67001288)** contains three directly installable APK files, each built and verified on the **exact same source commit** `67001288ba57b64ec945194c37319e6d71d25078`.

- **GeckoView, Android 8.0+**: `RudyBrowser-GeckoView-Android8-debug.apk` (real Firefox GeckoView + extensions, API 26+).
- **Compose Preview, Android 5.0+**: `RudyBrowser-Preview-Android5-debug.apk` (System WebView + Material UI, API 21+).
- **Legacy, Android 4.4+**: `RudyBrowser-Legacy-Android4-debug.apk` (System WebView, API 19+, older TV compatibility).
- `SHA256SUMS.txt` lists hashes of the original APKs. No ZIP extraction is needed; use the direct APK release assets.

This repository is private: downloads require a GitHub account with access. **All three are developer/debug-signed pre-release builds, not production releases**. Physical-device and production-signing gates remain open. To publish another **tested** pre-release, run the `Publish Rudy Browser developer pre-release` workflow manually with the exact source SHA and run IDs of the successful APK, GeckoView, Compose emulator, and Android TV emulator CI workflows. The workflow will refuse to publish when any of the four runs are not green for that same commit.


**Private GitHub repository:** `RudyCity/tv-browser` (the repository slug is unchanged).
**Android app label:** Rudy Browser. **Android application ID:** `io.rudycity.tvbrowser`
(unchanged, so compatible installs can be upgraded rather than installed as separate apps).

The app adapts to **phone portrait/landscape**, **tablets** and **Android TV/TV boxes**.
There are three independently installable Android paths: **legacy System WebView**
(API 19+), **Compose Preview System WebView** (API 21+), and **real Mozilla GeckoView**
(API 26+, application ID `io.rudycity.tvbrowser.gecko159`). Gecko ships a
real Firefox rendering engine and Firefox WebExtensions; no Chromium AAR or
Chrome Web Store runtime is bundled. The Gecko flavor is a debug preview,
not an approved production release.

### Native Chromium and Chrome Extensions — separate build route

The new [native Chromium build pipeline](native-chromium/README.md) pins a real
Chromium/Vanadium/Titanium source revision with Android Chrome Extensions support,
a locked CRX helper digest, a dedicated Android TV manifest patch and a native
APK validation gate. The source-only tests run in GitHub Actions; **the huge native
Chromium binary has not been compiled here and is NOT embedded in this APK**.
Until a genuine Chromium AAR is built, the native Chromium output is a **separate**
Android 10+ companion APK. Android 4.4–9 remains on System WebView. No automatic
native-to-WebView process recovery or Chrome Web Store support exists inside the
legacy APK.

## Network security: modern Preview versus old TVs

The modern Compose Preview denies unencrypted HTTP pages, HTTP redirects,
popups and downloads; the manifest declares `usesCleartextTraffic=false`.
For older Android versions where WebView does not reliably honor this flag,
the Java browser controller also checks URL schemes, WebView main-frame
navigation and resource interception. **Legacy** remains explicitly
HTTP-compatible for old TV firmware; use HTTPS whenever available.
This is a security milestone, not a completed release/security certification.
See [network security decisions](docs/NETWORK_SECURITY.md).

## Native Compose home screen (0.7.1)

The Preview's startup/empty browser home is now rendered using Jetpack Compose
and Rudy Material 3 tokens, with real actions for omnibox focus and four HTTPS
quick-access sites. The WebView stays behind the landing surface and remains
the renderer for all external websites. It reappears when the user navigates
to a website; Back to the internal homepage reveals Compose again. This
prevents an empty startup page if the emulator's old WebView renderer fails
to rasterize local HTML text. It **does not assert that webpage font raster
errors are fixed on that emulator**.

The 0.7.1 clean-install Preview uses a new package ID
`io.rudycity.tvbrowser.previewcompose071` to avoid debug signing conflicts
with previous 0.7.0 previews. Android 4.4/TV use the existing Java/WebView
home until their own UI and focus validation is complete.

## Kotlin + Jetpack Compose + Material 3 UI (0.7.0)

The phone/tablet **Preview** variant now uses Kotlin Jetpack Compose and
Material 3 to render the browser toolbar. The existing Java BrowserEngine,
popup/download approvals, WebView lifecycle, history, bookmarks, and extensions
remain owned by the same controller; the UI does not replace web content.

- Kotlin 2.1.20, Compose Compiler 2.1.20, Compose BOM 2025.05.01,
  Material 3, AndroidX Activity 1.9.3 lifecycle owners.
- Single-row omnibox on phones, extra navigation buttons on tablets,
  Material 3 overflow actions and slim loading indicator.
- Android 5.0+ (API 21+) Preview package:
  `io.rudycity.tvbrowser.previewcompose07`, installed separately so
  existing browser data is not touched.
- Android 4.4 (API 19–20) remains supported through the **legacy** Java
  toolbar; Android TV still uses large D-pad-focusable legacy controls
  until the Compose TV experience has real-device focus testing.
- If the optional Compose class is unavailable or fails to initialize,
  Java toolbar fallback remains visible, without replacing the engine.
- Real Gecko/WebView multi-tab is now implemented separately; independent embedded
  Chromium and Chrome .crx extensions are still not bundled.

See [Compose migration QA](docs/COMPOSE_MIGRATION.md).

## Chrome-inspired responsive browser UI (0.6.0)

The browser chrome is redesigned, **not the websites themselves**. A webpage
keeps its own colors and DOM without any injected "design system."

- **Phones under 600 dp** (320/360/390 included): one compact toolbar with Back,
  pill-shaped omnibox and overflow menu. No fake tab counter: true multi-tab
  management is available through the tab menu (independent renderer sessions).
  Go works through the Android keyboard action.
- **Tablets at 600 dp and wider:** a single toolbar adds Forward, Reload,
  Bookmark and optional Home/Extensions based on actual available width.
- **TV:** large, focusable D-pad buttons remain, with two rows on TV widths
  below 840 dp and one row on wider screens. The remote pointer still works.
- On phones/tablets, the old 26 dp always-visible footer is hidden. A 2 dp
  loading progress line appears only during navigation and disappears on finish.
  Errors are shown via a toast rather than being hidden in a footer.
- The omnibox displays the full URL while focused and the page hostname
  otherwise. The complete URL remains editable with one tap. Search or enter
  an address without losing text during overlapping page-load callbacks.
- Overflow groups frequent actions (Reload, Home, Forward, Bookmark,
  Extensions, Bookmarks, History) above advanced tools, engine settings,
  browsing data and native Chromium companion launcher.
- Rotation reparents existing controls without recreating active renderer sessions.
  Gecko includes HTML5 fullscreen viewport handling; the touch-device system bars
  remain visible outside fullscreen.
- Popup links and downloads still require explicit approval, and unsafe
  installer links remain blocked. The Gecko-only flavor declares runtime
  camera/microphone/location permissions, but a per-origin website prompt and
  Android OS grant are both required; other flavors remain unchanged.

**Visual implementation:** dark slate toolbar, low-contrast icon surfaces,
subtle pill-shaped omnibox, purposeful focus state and consistent touch sizes.
All screenshot QA on actual devices is pending.

## Previous phone and tablet behavior (0.5.0)

- Touch browsing, pinch zoom when supported by WebView, adaptive screen
  rotation and keyboard address entry; these functions remain supported.
- Original compact phone UI used two 56 dp toolbar rows and a persistent
  footer; this has been replaced to reclaim web viewport height.

## Native Chromium companion launcher (0.5.1)

When native Chromium is built and installed as a **separate APK** with package
ID io.rudycity.tvbrowser.chromium, open **Menu → Native Chromium + Chrome
Extensions**. The browser asks permission, then starts the companion only if
installed and supported by Android 10+. It deliberately does **not** transfer
the current URL, credentials or cookies to an unverified package. If missing,
you remain in Rudy Browser's System WebView. This is not automatic in-app
engine switching; the actual Chromium AAR embedder is still missing.

## Installing when Android reports "App not installed" (0.5.2)

Android rejects an APK update when the installed package has **a different
signing certificate**, even if the package ID and version are correct. Our
previous CI debug artifacts used ephemeral Android debug signing keys, so
their certificates were **not stable between build runs**. Do not uninstall
your old browser just to test a build.

The first **Rudy Browser Preview** APK used the package ID
`io.rudycity.tvbrowser.preview`. The 0.6 redesign preview now uses
`io.rudycity.tvbrowser.preview06`, intentionally different from both
the original browser and the earlier 0.5.x preview to avoid CI debug
signature conflicts. It installs **alongside** the existing
`io.rudycity.tvbrowser` without replacing its data. It intentionally
does not share cookies, bookmarks, settings or accounts with the old app.
The previous package continues to exist, with its same user data.

**Limit:** preview is still debug-signed using CI's per-run key. You can
install it freshly, but upgrading the same preview package from a later
CI run may again require uninstalling the preview. For reliable in-place
future upgrades, configure a persistent signing key via encrypted GitHub
Actions secrets (never commit a production private key). Until that is
configured, prefer testing independent clean-install previews.

For diagnostic ADB use, `adb install -r path/to/app.apk`. A
`INSTALL_FAILED_UPDATE_INCOMPATIBLE` response specifically confirms a
signature conflict. Different install errors (storage, SDK, security policy)
need separate fixes. Do not disable Play Protect or Android security checks.

## Browser features

- Back, forward, home, reload, address/search, bookmarks, recent history, WebView zoom,
  desktop user-agent toggle, clear cache/history, and controlled downloads
- Extension controls: built-in ad/tracker protection, dark mode, site-scoped userscripts,
  TV navigation helper and HTML5 video controls
- Conditional renderer selection: Auto / Embedded Chromium (when genuinely present) /
  System WebView. **A Chrome browser APK is not an embeddable Chromium AAR.**
- Conservative popup confirmation displays source, destination host and full URL.
  User-initiated popups open in the current page after approval, not as real browser tabs;
  multi-tab management is not yet implemented.

## Build and tests

Requires JDK 17, Android SDK Platform 35, Android Gradle Plugin 8.6.1, Gradle 8.7.

```sh
bash scripts/test-policy.sh
gradle --no-daemon testLegacyDebugUnitTest assembleLegacyDebug
# app/build/outputs/apk/legacy/debug/app-legacy-debug.apk
```

CI builds the API 19+ WebView fallback and uploads the
`rudy-browser-legacy-debug-apk` artifact. Optional Chromium builds require
`app/libs/chromium-engine.aar` passing `scripts/check_chromium_aar.py`.

For sideloading:

```sh
adb install -r app/build/outputs/apk/legacy/debug/app-legacy-debug.apk
```

The debug APK is **not** a signed production release. A Play Store release requires an
appropriate updated target SDK, policy review, signing and device compatibility checks.
Old system WebView/TLS/DRM/codec limitations cannot be fixed just by changing the app name.

## Technical docs

- [Chromium engine integration](docs/CHROMIUM_ENGINE.md)
- [Native Chromium + extension source/build pipeline](native-chromium/README.md)
- [Extension scope and limitations](docs/EXTENSIONS.md)
- [Implementation, test and release status](docs/VERIFICATION.md)
- [Rudy Material 3 Design System](docs/RUDY_DESIGN_SYSTEM.md)

## Device QA before release

Check 320 dp and 390 dp portrait, narrow landscape, 600–839 dp tablets, 840+ dp
tablets and Android TV with D-pad; verify address entry and IME, orientation changes,
back stack, popup default Block, download confirmation, fullscreen video, and TV pointer.


## Desktop builds (Windows, Linux and macOS)

Rudy Browser now has a separate Electron + Chromium desktop target. Android remains unchanged.

- Windows: NSIS setup installer and portable EXE.
- Linux: AppImage and Debian package.
- macOS: DMG and ZIP containing the app bundle (unsigned preview; signing/notarization is still required for public distribution).

See [desktop setup and build instructions](desktop/README.md). The [Desktop builds workflow](.github/workflows/desktop.yml) creates per-platform artifacts when a desktop change is pushed to main; artifacts are currently CI downloads, not a published GitHub Release.


## Desktop builds (Rust + Tauri)

Rudy Browser has a separate Rust + Tauri 2 desktop target for Windows, Linux and macOS. It uses native OS webviews and leaves the Android Kotlin/Java app unchanged.

- Windows: NSIS installer and MSI.
- Linux: AppImage and Debian package.
- macOS: DMG containing the app bundle (unsigned CI build).

Desktop setup, multi-webview security boundaries and build instructions are documented in [desktop/README.md](desktop/README.md). GitHub Actions produces per-platform CI artifacts; these are not yet a published desktop release.
