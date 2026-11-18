# Rudy Browser verification status (2026-11-18)

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
| Implemented | Code changes pending CI | Rudy Browser label and 0.5.0 version; responsive phone/tablet toolbar; touch navigation keeps Android bars; TV fullscreen, D-pad and pointer remain. |
| Standalone Chromium | Build route staged; no compiled Chromium APK yet | Locked Chromium 157 / Titanium extension-enabled source build recipe in native-chromium/ produces a **separate APK** when run on a large dedicated builder. The embedded `chromium-engine.aar` is still absent. |
| Tested | Pending CI for 0.5.0 | Pure-Java screen breakpoint/TV detection tests added to smoke and JUnit suites; Android APK build and unit tests must still pass in GitHub Actions. |
| Runtime verified | No | Physical phone, tablet, and TV/emulator tests not run. |
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
