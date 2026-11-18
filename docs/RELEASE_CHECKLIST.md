# Rudy Browser — release gates (2026-11-18)

This is a checklist, **not** an authorization to publish or sign a production APK.

## Current distribution

- `legacy` supports API19+ Android WebView and deliberately keeps old-firmware compatibility.
- `preview` supports API21+ Android WebView + Compose; an isolated debug ID prevents overwrite.
- `gecko` supports API26+ Firefox GeckoView and Firefox WebExtensions; its debug ID is `io.rudycity.tvbrowser.gecko159`.
- The **debug developer pre-release** [v0.7.1-preview.67001288](https://github.com/RudyCity/tv-browser/releases/tag/v0.7.1-preview.67001288) is published with direct Legacy/Preview/Gecko APKs and SHA256SUMS. This is not a production-signed or Play-approved release. No production keystore is in the repository.
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

The new Gecko variant now declares `targetSdk=36`, verified through an APK
manifest check in CI. Legacy and Compose Preview retain their historical
target for existing Android firmware compatibility. **This change alone does
not certify Play readiness:** edge-to-edge/window-inset, permissions,
package visibility, Android 15–17 device behavior and compatibility QA remain
mandatory. The historical legacy/preview `targetSdk=28` remains a Play
submission blocker; only the modern Gecko path is being prepared for release.

## Status rule

Report **Implemented** only for code committed; **Tested** only for passed
checks at exact SHA; **Runtime Verified** only for measured emulator/device
behavior; and **Released** only after a signed user-installable artifact is
published and verified. Do not label debug CI artifact a production release.
