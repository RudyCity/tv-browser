# Rudy Browser — Android phone, tablet and TV browser

**Private GitHub repository:** `RudyCity/tv-browser` (the repository slug is unchanged).
**Android app label:** Rudy Browser. **Android application ID:** `io.rudycity.tvbrowser`
(unchanged, so compatible installs can be upgraded rather than installed as separate apps).

The app adapts to **phone portrait/landscape**, **tablets** and **Android TV/TV boxes**.
The renderer is Android System WebView (API 19+); a true independent Chromium engine is
**not bundled**. Chromium integration is available only as an optional interface and
build variant that remains disabled until a validated native AAR is provided.

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

## Phone and tablet behavior (0.5.0)

- Touch browsing with a responsive home page, native pinch-to-zoom where supported by WebView,
  Android software keyboard for address/search and visible system status/navigation bars.
- Up to 839 dp available width: two compact toolbar rows (Back / Forward / address / Go,
  then Home / Reload / Bookmark / Extensions / Menu).
- From 840 dp: one toolbar row, preserving a flexible address bar.
- Rotation resizes/reparents existing toolbar controls **without destroying the active WebView
  or navigation history**. Activity state also records the current URL for process recreation.
- TV remains remote-first: fullscreen app, D-pad page navigation and optional remote pointer.
  Those TV-specific key interceptions are not applied to touch devices.
- Popup links and downloads still require explicit approval; installers and unsafe URL schemes
  are blocked. No extra device permissions are requested.

## Native Chromium companion launcher (0.5.1)

When native Chromium is built and installed as a **separate APK** with package
ID io.rudycity.tvbrowser.chromium, open **Menu → Native Chromium + Chrome
Extensions**. The browser asks permission, then starts the companion only if
installed and supported by Android 10+. It deliberately does **not** transfer
the current URL, credentials or cookies to an unverified package. If missing,
you remain in Rudy Browser's System WebView. This is not automatic in-app
engine switching; the actual Chromium AAR embedder is still missing.

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

## Device QA before release

Check 320 dp and 390 dp portrait, narrow landscape, 600–839 dp tablets, 840+ dp
tablets and Android TV with D-pad; verify address entry and IME, orientation changes,
back stack, popup default Block, download confirmation, fullscreen video, and TV pointer.
