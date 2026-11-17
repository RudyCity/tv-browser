# TV Browser — Remote-first Android TV browser

**Private GitHub repository:** `RudyCity/tv-browser`.

Dual-engine design: **standalone embedded Chromium when actually bundled on Android 10+, with
Android System WebView as fallback**, including legacy devices from Android 4.4 (API 19).

> **Status:** Android WebView engine and engine-routing source implemented; embedded native
> Chromium library is not supplied in this starter archive. A Chromium engine cannot be
> inferred merely from the presence of a `Chromium` toggle. No device APK runtime test yet.

## Architecture

- `:app` — Android TV / generic TV box UI, remote D-pad input, URL/search, bookmarks,
  history, fullscreen video, downloads, menu/settings and engine selection.
- `:engine-api` — Java contract shared between host app and an independent Chromium renderer.
- `WebViewEngine` — implemented fallback, uses system Android WebView.
- `EngineFactory` — attempts optional `EmbeddedChromiumProvider` via reflection on Android 10+;
  on missing or failed initialization, falls back to `WebViewEngine`. Engine status is visible.
- `:app:legacy` flavor API 19+; `:app:chromium` flavor API 29+ is disabled until the native AAR
  is supplied and packaging validation is satisfied. Chromium APK must contain engine binaries.
- `docs/CHROMIUM_ENGINE.md` — provider integration, build, compatibility, security, test gates.

## Built-in Extensions Manager (0.3.0)

Open **Ext** in the toolbar or **Menu → Extensions** with the TV remote.

- **Ad/tracker blocker** (on by default): conservatively intercepts subresources from
  a small, static tracker hostname list. This is *not* a full filter-list engine.
- **Dark mode** (off by default): injects page CSS after navigation.
- **TV navigation helper** (on by default): D-pad link navigation. Disabling lets
  WebView handle keys itself.
- **Userscript manager** (off by default): locally edit, save, or delete JavaScript for
  an exact HTTPS hostname at document-end. Explicit security confirmation required.

Switches persist. Every website has inherit/allow/deny controls; global **off**
always wins over site allow. Changes reload the current page.

**Compatibility:** these first-party add-ons currently run in **System WebView only**.
There is no Chrome extension API, Manifest V3 service worker, Chrome Web Store installation,
or CRX execution. The optional Chromium embedder needs its own add-on integration.
See [extension specification and test plan](docs/EXTENSIONS.md).

## Functions

- TV remote **D-pad**, OK, Back, Menu and USB keyboard/mouse
- Address/search bar, back/forward/refresh/home, local bookmarks and recent history
- Fullscreen HTML5 video *if device engine supports it*
- Desktop user agent toggle, zoom, local browsing data clearing
- Browser engine **Auto / Chromium / WebView** menu, explicit active-engine indicator
- Android DownloadManager support for normal HTTP/HTTPS links
- No analytics, trackers, account or unneeded permissions
- TLS certificate errors are never bypassed; file/content URI loading disabled in WebView

## Build

Requires JDK 17, Android SDK Platform 35, Android Gradle Plugin 8.6.1, Gradle 8.7.

```sh
# Pure-Java smoke test works without Android SDK
bash scripts/test-policy.sh

# Android 4.4+ fallback APK (no Chromium native SDK required)
gradle testLegacyDebugUnitTest assembleLegacyDebug
# -> app/build/outputs/apk/legacy/debug/app-legacy-debug.apk

# Only after independently producing a complete Chromium embedder AAR:
python3 scripts/check_chromium_aar.py app/libs/chromium-engine.aar
gradle testChromiumDebugUnitTest assembleChromiumDebug
# -> app/build/outputs/apk/chromium/debug/app-chromium-debug.apk
```

`chromium` flavor is disabled when the library is absent; the repository intentionally does not
ship a fake/prebuilt Chromium library. A normal Chromium/Chrome APK is **not** an embeddable AAR.
See [Chromium engine integration](docs/CHROMIUM_ENGINE.md).

GitHub Actions (`.github/workflows/android-apk.yml`) builds the legacy variant, then conditionally
checks and builds the Chromium variant only when a genuine AAR has been added. CI artifacts are
**debug APKs**, not signed production releases. Current Play Store target SDK rules are stricter
than this legacy sideload build target.

## Installing on TV

```sh
adb connect YOUR_TV_IP:5555
adb install -r app/build/outputs/apk/legacy/debug/app-legacy-debug.apk
```

For a device without ADB, transfer the APK via USB or a local file manager. Android 4.4–9 will
use its original WebView; this does not make old JS engines, DRM, Widevine, TLS stacks or codecs
modern. Consider OS updates where available. Avoid logging in to sensitive services from an
unpatched TV platform.

## Private repository

After `gh auth login`, in a clean extracted project directory:

```sh
bash scripts/create-private-repo.sh
```

The script is only for initial setup when a private repo does not exist. The remote repository
is already available; do not rerun repository creation.

See [Verification](docs/VERIFICATION.md) for honest implementation/test/release status.

App ID: `io.rudycity.tvbrowser`.
