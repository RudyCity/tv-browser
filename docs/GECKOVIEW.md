# Rudy Browser — real GeckoView (Android API 26+)

## Engine and build isolation

The `gecko` flavor uses the real Mozilla `org.mozilla.geckoview:geckoview:159.0.20261102144711` binary, not Android WebView or a Chrome APK. The independent install ID is `io.rudycity.tvbrowser.gecko159` (debug preview); API 26+ is required. `legacy` (API19) and `preview` (API21) continue using System WebView without changing their IDs or files. Cookies are **never migrated** between engines.

The official Gecko artifact needs Android compile SDK 37 and AGP 9.1+. The `gecko-build/` root uses AGP9.1.1, Gradle9.3.1 and built-in Kotlin2.2.10; the historical root stays on AGP8.6.1 / Gradle8.7 for old TVs. Install `platforms;android-37.0` from SDK preview channel then run `gradle -p gecko-build :app:assembleGeckoDebug`. CI verifies both build paths.

## Present capabilities (source implemented; verify each exact SHA with CI)

- Real process-wide GeckoRuntime with one GeckoSession and GeckoView per tab. Independent tab switching, closing, memory-conscious 3 TV/old or 6 modern cap, and human-approved HTTPS new tabs.
- Firefox WebExtensions are installed/listed/managed with Gecko's official WebExtensionController. An original, example.com-only extension is exercised by API29 emulator tests. Remote signed HTTPS XPI installation requires source and permissions approval; arbitrary Chrome CRX is **not** supported.
- Normal-tab URLs and selected position persist in app-private SharedPreferences and Activity Bundle, without storing forms or cookies. This restores URLs, not full back-forward and form state.
- Private tabs use `GeckoSessionSettings.Builder.usePrivateMode(true)` and never fall back to WebView. Private URLs are excluded from history, Activity saved state and the disk session list. All private sessions end when the Activity is destroyed; persistent private tab restore is intentionally disabled.
- Gecko site permission delegate displays the requesting HTTPS origin and prompts before supported content grants, while Android camera/microphone/location require separate OS grants. Unsupported high-risk privileges remain denied. A site permission viewer/reset is available from Tools. On gecko-only source manifests these permissions are declared; no silent OS grants.
- Gecko's own MediaSession delegate provides native play/pause, seek forward/back and mute controls for an active HTTPS media session; it never injects scripts. Exact seek intervals and speed changes are unavailable through this API and hardware video/DRM remains unverified.
- The native Find in Page UI uses the Gecko Finder instead of document JavaScript. TV remote PageUp/PageDown use native PanZoomController, and inactive Gecko sessions may be closed by explicit user approval (never automatic form loss).
- The TV pointer dispatches real user-triggered MotionEvents to the active Gecko or WebView renderer. Gecko fullscreen uses Mozilla ContentDelegate to hide browser chrome without reparenting the GeckoView.
- A Gecko website file upload requires an explicit consent prompt followed by Android's system document picker; only selected content:// URIs are passed to the page. Save Page PDF also uses Android's document picker with a background output stream. WebView uses Android PrintManager for Save as PDF.
- Clear browsing data waits for Gecko StorageController and WebView CookieManager completion callbacks and closes Gecko sessions before clearing. This confirms API completion, **not** forensic deletion from storage hardware.

## Limits — do not misstate these as released

The on-device TV remote/IME/focus experience, fullscreen video, DRM, signed XPI from a real publisher, protected downloads requiring Gecko cookies, upload picker, PDF export and private cookie isolation still need direct device/runtime proof. Some websites cannot work if they require restricted permissions. Manual zoom buttons for Gecko are not supported by its public programmatic zoom API; user pinch zoom works through native touch and requires device verification. No native Chromium AAR or Chrome Web Store exists.

A published **0.7.1 developer debug pre-release** exists. **0.8.0 beta is in source development only** until exact-SHA CI and manual release publication. No production signing, physical Android phone/tablet/TV test, complete API26/29/34/36 matrix, or Play policy approval has been verified. CI `Android GeckoView Preview` includes a real API29 emulator, a first-contentful-paint HTTPS check, and Firefox extension smoke coverage. Check the **latest HEAD** GitHub Actions before making a pass claim.
