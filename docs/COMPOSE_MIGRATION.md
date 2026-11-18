# Kotlin + Jetpack Compose + Material 3 migration

## Architecture

The new :compose-ui Android library contains the Kotlin + Material 3 browser
toolbar. The Java MainActivity remains the browser controller and owns:
WebView/BrowserEngine, navigation, bookmarks, history, popup consent, download
consent, user scripts, engine selection and renderer fallback.

The engine-api module exports a small API-19-safe BrowserChrome interface.
MainActivity reflects the optional ComposeBrowserToolbar **only for touch
devices on API 21+**. It retains and can display the old Java toolbar if
this class is absent or fails to initialize. No storage migration occurs.

The Compose UI uses AndroidX ComponentActivity lifecycle/saved-state owners to
ensure ComposeView is attached to a valid Activity lifecycle. The APK uses
Material 3, an explicit dark slate color scheme, one-line address/search
field, loading progress line, and an overflow menu wired to Java callbacks.
It deliberately does not reproduce webpage content in Compose.

## Compatibility matrix

| Flavor/device | UI | Min SDK | Engine |
| --- | --- | --- | --- |
| legacy, API 19–20 | Java Views | 19 | System WebView |
| legacy, API 21+ | Java Views fallback | 19 | System WebView |
| preview, API 21+ phone/tablet | Kotlin Compose + Material 3 | 21 | System WebView |
| preview, Android TV | Large focusable Java toolbar until TV focus QA | 21 | System WebView |
| chromium when real AAR supplied, API 29+ touch | Kotlin Compose presentation | 29 | Conditional native engine |
| independent Chromium companion | Separate app not built yet | 29+ | Not embedded here |

Preview package ID is io.rudycity.tvbrowser.previewcompose07 to avoid update
signature conflicts with previous debug builds. Debug preview runs side-by-side
and has independent data; never uninstall an existing app to test the UI.

## Test gates

Automated via Android GitHub Actions:
- Java pure tests, including layout and URL policy
- :compose-ui:testDebugUnitTest (compact URL formatting and Kotlin compilation)
- testLegacyDebugUnitTest / testPreviewDebugUnitTest
- assembleLegacyDebug / assemblePreviewDebug
- aapt verifies distinct package names, preview app label and signed APKs

Manual QA required before any production release:
- Android 5+, phone 320/360/390 dp: single row, keyboard Go, URL edit,
  search, back, overflow, home/reload, bookmark, extensions, permissions
- Tablet 600/768/840/1280 dp: responsive navigation; rotation during load,
  scroll and fullscreen video without losing WebView session or activity
- Android TV with remote: existing focus/long-press behavior preserved
- Android 4.4: legacy Java shell can open and browse; Compose never loads
- Saved bookmarks and browsing data remain unchanged after UI migration
- Popup approval, downloads, TLS errors, offline recovery and renderer crash
  handling remain functional
- Runtime screenshot and interaction verification on actual hardware

## Not implemented by this migration

Compose does not itself introduce a Chromium binary, Chrome Web Store support,
browser tab management, full-screen content filters or modern site support on
very old WebView firmware. Do not claim these capabilities until separately
implemented, built, and runtime tested.
