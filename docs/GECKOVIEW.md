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

## Separate modern build toolchain (CI finding 2026-11-18)

GeckoView 159.0.20261102144711 declares `compileSdk >=37`. Its transitive AndroidX libraries require AGP 9.1+. The original project uses AGP 8.6.1 and Gradle 8.7 and must continue building API19 legacy without modification. **Do not bypass AAR metadata validation**. A second Gradle root (`gecko-build/`) reuses exactly the same sources with AGP 9.1.1 / Gradle 9.3.1 / compileSdk 37 and Kotlin 2.2.10. The root project preserves the legacy/Preview toolchain. Install Android platform 37; execute `gradle -p gecko-build :app:assembleGeckoDebug`. This is a build migration path, not yet a passing build proof.

AGP 9's built-in Kotlin cannot apply old `org.jetbrains.kotlin.android` side-by-side. The modern build instead selects `compose-ui/build-gecko.gradle`, with built-in Kotlin enabled. The original `compose-ui/build.gradle` remains untouched for AGP 8.6.1.

## Firefox WebExtensions (implemented in code, verification pending)

When Gecko is active, the Extensions UI uses the real `WebExtensionController`, not WebView JavaScript extras. The bundled original `Rudy Page Marker` add-on runs only on `https://example.com/*` without privileged permissions and must be installed by a human. Signed remote `.xpi` install requires first a reviewed HTTPS source URL, then a Mozilla permission-origins-data-collection consent dialog; a canceled dialog denies the install. Gecko validates XPI signatures. Installed add-ons can be listed, disabled/enabled, and uninstalled, with on-device persistence handled by Gecko. This is not Chrome CRX support. No automated tests have passed for this slice at time of commit; emulator extension acceptance evidence is still pending.

## Explicit site permission policy (2026-11-18)

The Gecko flavor sets a `GeckoSession.PermissionDelegate` that denies Android runtime permission requests, audio/video capture, and content permissions. The current preview therefore intentionally **does not support website camera, microphone, geolocation or notification permissions**. A future permission manager must display the exact HTTPS origin, request Android platform permissions separately, respect revocation, and provide a deny-by-default TV remote workflow; never silently grant permissions or claim the feature works. This is a secure preview limitation, not a fully implemented site permission manager.

## Real multi-tab phase slice

The browser controller now owns one independent `BrowserEngine` per tab (and therefore separate GeckoSessions or WebView instances). Selecting another tab pauses and hides the previous renderer without destroying its page state. Background callbacks may update their own tab metadata but **cannot** trigger foreground popups, downloads, fullscreen or progress. Confirmed popup links open a *new* real tab. A low-memory cap applies (3 old/TV, 6 compatible touch). Switching the engine asks for confirmation before discarding other tabs and explicitly warns cookie/session storage is not migrated. Closing all tabs destroys sessions and creates a clean start page.

Current limits: tab thumbnails, private browsing, memory eviction, disk-based session restoration after full process death (only activity Bundle URL restart is provided), and background renderer crash recovery are not completed. Cross-tab global history/data clearing is requested with old tab surfaces closed; Gecko async deletion is **not** yet proven and the UI must never say it was verified. Real Gecko and WebView tab lifecycle tests are in the source tree, with actual runtime status governed by exact-SHA emulator CI rather than the presence of code.
