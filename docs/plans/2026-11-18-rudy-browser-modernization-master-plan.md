# Rudy Browser modernization — phased master plan
Date: 2026-11-18
Repository: private `RudyCity/tv-browser`
Baseline: `be92071277ff711ba621f649bad27989016bb1f7` (0.7.0)
Owner direction: Kotlin + Jetpack Compose + Material 3; maintain Android phone/tablet and TV/legacy compatibility.

## Non-negotiable engineering rules

- Keep the `main` branch, without reset/force push/checkout/worktree changes. Preserve unrelated changes and existing browser data.
- **No fake Chromium or extensions.** API 19–20 stays on Android System WebView and Java Views. API 21+ modern Preview uses Compose on touch devices; TV uses remote-friendly Java Views pending TV runtime validation.
- Do not deploy, upload Play Store builds, migrate data, modify credentials, or enable dangerous browser permissions without approval.
- Keep WebView ownership in the Java browser controller until lifecycle migration is demonstrably safe. Compose is presentation only.
- Human approval for new windows/downloads and existing URL scheme restrictions remain active. Never auto-open installer links.
- Report status separately: **Implemented**, **Tested (CI)**, **Runtime Verified**, **Released**. Never conflate green CI with on-device behavior.
- Each phase requires its own non-force commit and a passing CI run before marking **Tested**. If CI is red, repair it before moving further.

## Phase tracker

| Phase | Priority | Work | Definition of done | Status at plan creation |
| --- | --- | --- | --- | --- |
| M0 | P0 | Stabilization gates | Fix duplicate progress indicators, ensure optional Compose fallback visible and logged; pure tests & both APKs pass | Tested in CI; real-device QA pending |
| M1 | P0 | Runtime/navigation/lifecycle | Keyboard Go and fullscreen/back, renderer failure/rotation handling, navigation state and popups/downloads regression; instrumented test harness | Emulator startup/progress tested; remaining runtime behaviors pending |
| M2 | P0 | Network security & release safety | Restrict cleartext on modern flavor (preserve explicitly documented TV legacy behavior); target SDK compliance upgrade after testing; reproducible signing/upgrade path and no embedded secrets | First HTTP security slice tested (CI + API29 emulator); SDK/signing pending |
| M3 | P1 | Rudy Design System | Theme/tokens, spacing, typography, shapes, contrast, responsive components, accessibility and empty/loading/error states | Started: dark tokens, 48dp touch targets, emulator screenshot capture; remaining QA pending |
| M4 | P1 | Complete Material 3 migration | Bookmarks/history, settings, extension manager, downloads and consent dialogs modernized without losing controller security | Main/advanced menus tested on API29; remaining dialogs pending |
| M5 | P1 | Real browser functionality | Genuine multi-tab manager, tabs/session restoration, private data model, better download manager and navigation | Not started |
| M6 | P1 | Android TV UX | Dedicated D-pad/focus/remote keyboard/fullscreen/pointer QA, Android TV Compose when verified, maintain legacy TV | Not started |
| M7 | P1/P2 | Chromium + extensions | Build native Chromium with a supported runner, verify APK on real ARM64 Android, install/uninstall MV3 extensions; legal/source obligations | Not started |
| M8 | P2 | Release readiness | Accessibility audit, performance baseline, real-device matrix, CI screenshot/instrumentation gates, permanent signing, publication review | Not started |

## M0 — presentation stability and build quality (first implementation)

1. Eliminate **double loading progress**: the Java 2dp `ProgressBar` is visible only when the Java toolbar is active. The Compose toolbar owns progress on modern touch devices.
2. Add an API-19-safe pure-Java policy for toolbar/progress presentation, with JUnit and Java smoke tests across Legacy/Compose/TV/fallback combinations.
3. Surface failures of the optional Compose toolbar gracefully, without crashing or hiding the browser. Logging and an informative one-time UI notice are preferable to silent fallback.
4. Keep the safety-critical Java renderer event callbacks and existing single-tab semantics.
5. Update documentation and CI tests (including the Compose Kotlin test suite). Verify both legacy and Preview APK builds and their distinct package IDs.

**Exit criteria:** source commit + green workflow on exact SHA, no loss of legacy debug APK. Runtime is still **unverified** until tested on a device.

## M1 — interaction and lifecycle

- Browser navigation: keyboard Go, full edit URL, hostname display, Back/Forward/Home/Reload, rotation while loading, external URL intents.
- Handle renderer process death, failed engine init, full-screen video restoration, and Android back correctly; no infinite recovery loop.
- Add Compose UI instrumentation tests on supported emulator/API versions. Existing unit tests alone cannot validate focus or touch interaction.
- Recheck WebView popup probe isolation, approval expiry, download consent, TLS errors, and old provider behavior.
- Only mark Runtime Verified after emulator/hardware execution with logs and screenshots.

## M2 — Android security/release

- Modern Preview: default deny cleartext network traffic and make exceptions explicit and testable; legacy compatibility separately documented. Do not remove user consent.
- Android SDK target: audit breaking changes for current Google Play policy and Android TV exemptions before increasing `targetSdk`. Conduct device and scoped storage/permissions tests.
- Introduce persistent signing strategy via encrypted CI secrets or authorized keystore service (never commit signing private material). Debug keys are not upgrade-stable.
- Add dependency/license/SBOM and Android lint/security policy checks; block release on critical findings.

## M3–M4 — design system and full UI migration

- Define Rudy tokens (color, typography, shape, spacing, focus, motion) in Compose, tested under font scaling, 320/390/600/768/840dp.
- Prefer Android Material 3 defaults and high information density; no arbitrary oversized cards, thick borders, or decorative gradients.
- Preserve intentional web viewport size and native page colors; user websites must never be redesigned through CSS injection.
- Replace Java AlertDialogs incrementally with Compose secure consent flow, maintaining focus on the safe option on TV and always showing the real target URL.
- Build coherent menus, bookmarks/history, engine settings, extension manager and downloads; keep accessibility semantics.

## M5 — browser model

- Tabs must have **real independent WebView instances** and lifecycle/suspend/cleanup policy; do not display a fake counter.
- Explicit memory budgets for TV/old devices; restore tabs safely and fail closed for stale/private data.
- Downloads include verified filename, origin, mime type, human approval, progress and errors.

## M6 — TV and legacy

- Android TV: list and validate remote focus semantics, back key, D-pad pointer, 10-foot typography, keyboard IME, video, long-running sessions and leanback launcher.
- Android 4.4–9 on legacy variant: continue to compile/test without Compose dependency in the installed APK.
- Keep limited engines and unsupported DRM explained honestly.

## M7 — real native Chromium & Chrome Extensions

- `native-chromium/` is a pinned source-build recipe, **not a working native browser APK**.
- Requires a disposable Linux x64 runner with >=120GiB free disk and >=16GiB RAM; manual build only.
- Check extension install/update/security prompts, permissions, MV3 APIs, persistence, DRM/video and sandbox on real ARM64 device. Do not claim Chrome Web Store in the WebView variant.
- Preserve GPL and third-party source distribution obligations.

## M8 — release evidence

- Automated: Gradle Java/Kotlin unit tests; Compose instrumentation; API 19 legacy, API 21+ phone, tablet and TV emulator; aapt/apksigner checks.
- Manual: physical phone and Android TV remote, rotation, accessibility font scaling, memory, installs/updates, incident logs/screenshots.
- Production only with persistent signing key under proper custody, target SDK policy compliant, release notes and consented distribution.

## Evidence log

- 2026-11-18 baseline: CI `44216420225` **success** for 0.7.0 Kotlin Compose Preview + Java legacy. No reported physical runtime verification.
- M0 implementation: record exact commit/run URL and results below, including any red runs and fixes.

- M0 code: presentation policy with JUnit + smoke assertions, fallback cleanup + user notice, progress visibility owned by exactly one toolbar. Recheck CI and record its run before changing status to Tested.

- M0: `5bb61b7f` implemented presentation policy; `0d5c9f2d` fixed generated BuildConfig; Android APK run `44218579316` **success** (Java/Kotlin unit tests, legacy and Preview APK build, IDs/signing checked). No phone/TV physical runtime check.
- M1 started: Android API 29 emulator instrumentation suite and its CI workflow; Android touch startup and Java/Compose progress gate, not a replacement for rotation/popup/TV/device QA. Verify CI before marking this slice tested.

- M1: commit `25042161` added API 29 Android emulator CI. Build run `44219194544` succeeded. Emulator run `44219194576` succeeded, with **2 actual instrumentation tests / 0 failures** (Composer toolbar and WebView attached; one loading indicator only). Physical phone, TV, rotation, keyboard and popup QA still pending.
- M2 first slice: modern flavor denies HTTP in Android manifest, direct navigation, redirects, main-frame resource interception, popup and download entrypoints. Java unit/smoke tests and Preview API29 NetworkSecurityPolicy instrumentation assertion added. Legacy flavor still allows cleartext for old TV firmware. Mark Tested only after CI and emulator run both pass.

- M2: commit `8bdc2e53` blocked cleartext for modern Preview while keeping legacy TV HTTP; Android APK run `44220329584` succeeded including manifest checks; API 29 emulator run `44220329665` succeeded with **3/3 instrumentation tests and 0 failures**, including Android NetworkSecurityPolicy denying cleartext. Physical API 21–25 WebView and TV testing remain pending.

- M3 first slice: added a centralized Material 3 `RudyDesignSystem.kt` (palette/typography/shapes/spacing/sizing), replaced ad-hoc toolbar colors/sizes and raised icon touch targets to 48dp. Emulator CI now saves a startup screenshot for visual review. Source implementation pending exact-SHA unit/emulator CI.

- **M3 screenshot defect (2026-11-18):** CI `44221584684` reported success but `compose-startup.png` showed the Android launcher, not Rudy Browser. GitHub logs contained `Error type 3` from `am start` after instrumentation had uninstalled the app. Corrective workflow reinstalls APK, checks `pidof` and foreground `mResumedActivity`, and captures screenshot only if truly running. Original M3 visual evidence is invalid. Await replacement workflow result.

- M4 first slice: composable Material 3 action dialog added to optional browser chrome and wired to existing Java main/advanced menu handlers; legacy Android and TV retain their native menus. Dismiss does not call actions. API29 instrumentation tests menu visibility and explicit tap execution. CI evidence pending.

- M3 screenshot gate fix: commit `aaa0b9e`; Android APK workflow `44224115677` **success** and emulator workflow `44224115486` **success**. Correct screenshot captures Rudy Browser foreground (Compose Back + omnibox + overflow), after reinstalling the app and asserting resumed Activity + PID. The earlier launcher screenshot is superseded; background remains dark because local home-page content has not yet been separately validated by DOM/render tests.

- M4 evidence: commit `259d3dfb`; Android APK run `44225144764` success, emulator run `44225145364` success with **4/4 tests**, including an actual Material 3 action selection. M4 is still partial; Bookmarks/History/Extensions screens and consent dialogs remain.
- M1 home-page regression coverage in progress: additional API29 instrumentation checks local home-page DOM and visible foreground pixels. The prior screenshot showed the Compose toolbar but a uniform dark WebView area. This test aims to distinguish loading latency from an actual blank-page defect. Do not mark until emulator result.

- M1 rendering regression: API29 emulator run `44225900178` **failed** (4 previous smoke tests passed; new test found DOM text present but rendered screenshot lacked text). Logcat showed Chromium GL `glRasterCHROMIUM: Invalid font buffer` using deprecated `swiftshader_indirect`. Changed emulator only to `-gpu swiftshader -feature -Vulkan` as recommended by Android emulator troubleshooting; test remains strict. Await fresh runtime evidence before deciding if this is an emulator bug or application defect.

- M1/M3 home rendering: API29 WebView DOM text existed but no rasterized font glyphs; `swiftshader_indirect` -> supported SwiftShader and Vulkan disabled in `3cd4b1b` did **not** fix it (emulator run `44226738207` red). A genuine Compose Material 3 landing overlay now renders local homepage text/links on modern Preview while preserving WebView as site renderer. Emulator screenshot and navigation assertions must pass before this slice is counted tested. This is **not** evidence that Chrome/WebView remote-site glyph rendering on API29 is repaired.
