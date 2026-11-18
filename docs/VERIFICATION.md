# Rudy Browser verification status (2026-11-18)

## M1 emulator evidence and M2 network-security slice

- API 29 emulator workflow `44219194576`: 2 real instrumentation tests succeeded,
  confirming Compose toolbar and WebView attachment and preventing duplicate
  Java loading progress. Other runtime behaviors are not yet verified.
- Modern Preview cleartext restrictions implemented/tested on commit `8bdc2e53`:
  Android APK workflow `44220329584` passed manifest + unit tests, and
  API29 emulator workflow `44220329665` passed 3 real instrumentation tests,
  including `NetworkSecurityPolicy` denying HTTP. Legacy TV HTTP remains
  deliberately allowed. Other SDK versions and devices are still unverified.
- targetSdk policy compliance, production signing and native Chromium are
  still outstanding; do not release this debug build as production.

## Compose UI migration (0.7.0)

Implementation staged: Kotlin Compose + Material 3 presentation module for
Preview Android 5.0+ touch devices, using an API-19-safe Java controller
bridge; the legacy APK retains Java Views and Android 4.4 support.
The runtime UI has **not** been tested on a physical Android device.
See [the migration matrix](COMPOSE_MIGRATION.md) for verification gates.

## Chrome-inspired UI redesign 0.6.0

Implemented in source: compact single-row phone toolbar, single-row tablet
navigation, separate large-focus TV layout, hidden phone/footer, slim progress
bar, compact hostname display with full URL on focus, and a two-level browser
menu preserving prior actions. Pure Java layout decision tests cover
320/390/600/768/840/1280 dp and TV/mobile distinctions. GitHub Actions must
still verify this commit and real phone, tablet and TV runtime remains untested.

No genuine multi-tab feature, independent embedded Chromium AAR or full Chrome
extensions runtime is implied by this UI change.


| Stage | Result | Details |
| --- | --- | --- |
| Implemented | M0 + first M1/M2 slices | Compose single-progress/fallback, API29 instrumentation and modern-Preview HTTP restrictions; plan at `docs/plans/`. |
| Standalone Chromium | Build route staged; no compiled Chromium APK yet | Locked Chromium 157 / Titanium extension-enabled source build recipe in native-chromium/ produces a **separate APK** when run on a large dedicated builder. The embedded `chromium-engine.aar` is still absent. |
| Tested | CI green on `8bdc2e53` | JUnit, Java smoke, Kotlin test, both APK builds, package/signature and cleartext manifest checks; 3 API29 instrumentation tests passed. |
| Runtime verified | API29 emulator smoke only | Compose Activity/WebView startup, single loading progress and cleartext policy tested. Physical phone/TV, lifecycle, redirect and download runtime tests remain. |
| Released | No production release | CI produces debug APK artifacts only, not a signed production APK. |
| GitHub repository | Private | `RudyCity/tv-browser`; repository name remains unchanged. |

## Native source-build progress

- Added upstream SHA and helper-CRX SHA256 pins, an isolated native builder script,
  Android TV Leanback manifest customization, APK structural checks and source-only
  Python tests. CI should verify these without downloading a 100+ GiB Chromium tree.
- Full native compilation is intentionally manual-only and requires a dedicated
  self-hosted builder labeled chromium-builder. No APK, on-device verification,
  Chrome extension installation test, or production release is claimed.
- Chromium native APK and legacy WebView APK are separate apps; a single-APK
  embedded renderer with automatic fallback remains unimplemented.

## Remaining validation

- Ensure GitHub Actions passes `scripts/test-policy.sh`,
  `testLegacyDebugUnitTest` and `assembleLegacyDebug` on this commit.
- On phone and tablet: verify 320/390 dp portrait, landscape, rotation while browsing,
  keyboard input, system navigation bars, address visibility, link tapping,
  zoom, download confirmation and blocked popups.
- On Android TV and old TV boxes: verify D-pad navigation, pointer, fullscreen video,
  remote focus and fallback renderer compatibility.
- A real independent Chromium embedder remains a separate engineering milestone.
  It must supply native libraries, renderer, sandbox, security updates and the
  `:engine-api` contract; old WebView engines do not gain modern Chromium behavior.
- Before public release: update Play Store target SDK, verify device compatibility,
  establish production signing, and test instrumentation/security/performance.
