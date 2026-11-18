# Rudy Browser 0.8.0 Beta — implementation, evidence and blockers

Date: 2026-11-18 · repository: `RudyCity/tv-browser` · branch `main`.
This document is a **source implementation tracker**, not an assertion that 0.8.0 is a finished or production-signed binary. Never infer actual-device behavior from a successful compilation.

| Phase | Implemented source slices | Test/verification status | Remaining blocker |
| --- | --- | --- | --- |
| P0 privacy + stability | Private Gecko tabs fail closed on renderer crash; private popup remains private; engine switches never transfer private URL; storage clearing waits on callbacks; explicit foreground-only prompts | Pure Java session policy regression; existing Gecko API29 privacy/persistence instrumentation; exact latest CI required | True physical-device cookie isolation, crash/restart and permission denial verification |
| P1 Android TV | Native Gecko/WebView page scrolling, TV-sized controls, focusable tab cards, memory cleanup with explicit consent; Gecko MediaSession play/pause/seek/mute (no JS injection); new true Android TV emulator CI | First Android TV API29 emulator workflow passed on `84c3b6ec`; Gecko media device verification pending | Remote pointer, TV IME, DRM/fullscreen, sustained playback and low-memory physical TV proof |
| P2 browser features + UI | Native Find in Page, normal/private tab grid (actual separate sessions), Android Downloads handoff, light/dark modern Compose browser chrome without styling website, PDF/file picker features carried forward | Existing APK/Gecko/Compose API29 checks; new theme compilation repaired after observed red CI at `7b098e48` | Comprehensive screen reader, 320/390/768/1280, RTL, tab thumbnails and updated latest exact-SHA CI |
| P3 beta + distribution | VersionCode 12 / versionName 0.8.0 without changing installed app IDs; manual 4-CI beta release gate with immutable APK assets and checksum; optional manual signed Gecko candidate with owner-managed environment | Existing 0.7.1 prerelease published; beta workflows in source only | Owner-provided signing keys, signing certificate/upgrade validation, hardware and Google Play policy validation; no 0.8.0 beta release until verified CI |
| P4 native Chromium | Pinned source+CRX digest, AArch64 ELF and signature-aware APK verifier, provenance JSON, isolated manual runner | Source-only Chromium policy/patch tests passed on `9396576f`; actual native build not run | Dedicated Linux builder (120 GiB SSD, ≥16 GiB RAM), upstream binary compilation, ARM64 Android hardware extension/MV3 test and license distribution review |

## CI and release contract

- Source tasks are only **Implemented** when committed.
- **Tested** requires relevant workflows to pass on the exact SHA. A green earlier run does not certify a newer commit; Android APK, Gecko API29, Compose API29 and true TV API29 are separate checks.
- **Runtime Verified** applies only to behavior actually exercised in an emulator or physical device; physical-device testing is not inferred.
- **Released** applies to an actual published artifact, with its build source, checksum and signing type described. The existing `v0.7.1-preview.67001288` is a debug developer release, not 0.8.0.
- No automatic GitHub Release publishing or production credentials changed. The opt-in signing workflow needs an environment named `android-release-candidate` and four explicitly configured secrets: `RUDY_ANDROID_KEYSTORE_BASE64`, `RUDY_ANDROID_STORE_PASSWORD`, `RUDY_ANDROID_KEY_ALIAS`, `RUDY_ANDROID_KEY_PASSWORD`.
- Before a beta release, verify all four workflows on the exact same source SHA, then run `Publish Rudy Browser developer pre-release` with their run IDs. This is a user action, not an automatic deployment.

## Not-yet-complete product work

Full Gecko zoom command implementation, tab screenshots, update-stable production signing, Play compliance of legacy/preview, full Android 26/34/36 regression matrix, Android TV physical remote/IME/media sessions, real native Chromium engine, physical Android compatibility, and Play/production publication remain open. Do not claim otherwise.
