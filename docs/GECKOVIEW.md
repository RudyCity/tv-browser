# Real GeckoView engine preview

The `gecko` flavor packages the official Mozilla GeckoView stable binary; **it is not System WebView**. It is a separate, debug-only install ID `io.rudycity.tvbrowser.gecko159` and requires Android API 26+. System WebView `legacy` API19+ and `preview` API21+ remain separate, unchanged app IDs. This build never migrates cookies between engines, and never claims Chrome CRX compatibility.

- Module: `engine-gecko`, implementation: `io.rudycity.tvbrowser.gecko.GeckoEngine`.
- Runtime: process-wide `GeckoRuntime.getDefault`; per-engine `GeckoSession`, owned `GeckoView`; explicit close and detach.
- The controller delegates HTTPS navigation, progress, back/forward, title, crash recovery to Gecko. Unsupported schemes and unattended new windows are denied. A user-gesture HTTPS new-window request reaches the existing human-approval UI and opens in the current tab, not a fake new tab.
- Downloads: only HTTPS external responses can reach the existing approval flow. Gecko cookies are deliberately **not** read using WebView's CookieManager.
- Default content permissions stay denied until a complete Gecko site permission manager is implemented.
- Current limitations: extensions/XPI manager not wired yet; per-page Gecko zoom not implemented; independent tab sessions, video fullscreen, TV remote focus, and image/content render evidence still need testing. Do not report these as finished.
- Preview command: `gradle :app:assembleGeckoDebug`. Emulator: `gradle connectedGeckoDebugAndroidTest`.

Versions: `org.mozilla.geckoview:geckoview:159.0.20261102144711` pinned from Mozilla Maven. See GeckoView official [quick start](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/consumer/geckoview-quick-start.html) and [WebExtensions documentation](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/consumer/web-extensions.html). Version and minimum API should be verified against resolved AAR manifest in CI, not presumed from a high-level browser release announcement.
