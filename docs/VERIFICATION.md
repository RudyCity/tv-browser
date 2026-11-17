# Rudy Browser verification status (2026-11-18)

| Stage | Result | Details |
| --- | --- | --- |
| Implemented | Code changes pending CI | Rudy Browser label and 0.5.0 version; responsive phone/tablet toolbar; touch navigation keeps Android bars; TV fullscreen, D-pad and pointer remain. |
| Standalone Chromium | Not integrated | No `chromium-engine.aar` present. `chromium` flavor remains gated; only the WebView fallback is buildable in this repo today. |
| Tested | Pending CI for 0.5.0 | Pure-Java screen breakpoint/TV detection tests added to smoke and JUnit suites; Android APK build and unit tests must still pass in GitHub Actions. |
| Runtime verified | No | Physical phone, tablet, and TV/emulator tests not run. |
| Released | No production release | CI produces debug APK artifacts only, not a signed production APK. |
| GitHub repository | Private | `RudyCity/tv-browser`; repository name remains unchanged. |

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
