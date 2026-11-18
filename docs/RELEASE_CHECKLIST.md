# Rudy Browser — release gates (2026-11-18)

This is a checklist, **not** an authorization to publish or sign a production APK.

## Current distribution

- `legacy` supports API19+ Android WebView and deliberately keeps old-firmware compatibility.
- `preview` supports API21+ Android WebView + Compose; an isolated debug ID prevents overwrite.
- `gecko` supports API26+ Firefox GeckoView and Firefox WebExtensions; its debug ID is `io.rudycity.tvbrowser.gecko159`.
- No official GitHub release/tag signed for distribution; no production keystore is in this repository.
- Native Chromium remains a source-only experiment. Do not advertise Chrome Web Store or CRX support.

## Must pass before a production release

1. Repeat exact HEAD SHA CI for legacy APK, Preview API29 emulator and Gecko API29 build+instrumentation, retain screenshots, APK manifest, SHA256 and logs.
2. Verify Android API26/29/34/36 on at least one real Android phone and tablet, including rotation, IME, link popup approval, site permissions, file picker, PDF export, secure/insecure HTTPS and memory recovery.
3. Verify legacy API19/23 TV boxes and modern API29/34 Android TV D-pad, remote cursor, IME, video/fullscreen/DRM, low-memory (3 tabs) and real HDMI output; record device/firmware.
4. Prove private tabs isolate cookies/cache from normal profiles and never persist URLs, forms or history even on crash/process death.
5. Confirm clearing data prevents re-accumulation from open sessions and verifies cookie/storage deletion for both rendering engines.
6. Exercise signed XPI installation, extension updates, permissions, disable/re-enable/uninstall; reject unsigned and malicious add-ons.
7. Audit security-sensitive file uploads, authenticated downloads, PDF save, embedded origins, TLS failures and code/permission prompts.
8. Measure reproducible cold start, memory footprint per tab, ANR, crash recovery, video FPS/CPU over long TV sessions.
9. Build release with a persistent, securely managed signing key. Confirm package ID / versionCode upgrades and that the same key signs subsequent versions. **Never commit private signing keys.**
10. Review Mozilla/Chromium/AndroidX and extension notices, privacy disclosures, software license compliance, accessibility and required Google Play target API.

## Google Play target policy (as of November 2026)

Google Play submission of new phone/tablet apps and updates currently requires
target Android API 36+, while Android TV apps may target API 34+ per Google's
published exceptions:
https://support.google.com/googleplay/android-developer/answer/11926878?hl=en

The repo's historical `targetSdk=28` remains a **release blocker**.
Updating the target must be followed by edge-to-edge/window-inset,
permissions, package visibility, behavior and compatibility QA. Merely
changing an integer would not certify production readiness.

## Status rule

Report **Implemented** only for code committed; **Tested** only for passed
checks at exact SHA; **Runtime Verified** only for measured emulator/device
behavior; and **Released** only after a signed user-installable artifact is
published and verified. Do not label debug CI artifact a production release.
