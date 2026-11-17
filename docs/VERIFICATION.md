# Verification status (2026-11-18)

| Stage | Result | Details |
| --- | --- | --- |
| Implemented | Partial | WebView fallback, engine abstraction, auto/manual preference, runtime fallback, crash-loop guard, engine indicator, package/flavor gates. |
| Standalone Chromium | Not yet integrated | `chromium-engine.aar` containing a real maintained Chromium renderer, native libraries, sandbox and resources **not present**. |
| Tested | CI green on 0.3.1; pending 0.4.0 | GitHub Actions previously built and unit-tested the Android legacy APK. The 0.4.0 changes still require their own CI run. |
| Runtime verified | No | No TV hardware/emulator or Android SDK available. No native Chromium behavior has been tested. |
| Released | No production release | Previous GitHub Actions produced debug-signed legacy APK artifacts; no production signing or public release. |
| GitHub private repository | Created | `RudyCity/tv-browser` exists with private visibility; source sync is managed separately. |

## Gates remaining before release

- Acquire or build a **genuine, maintained**, independent Chromium embedder for Android 10+
  and implement the `:engine-api` provider contract, including sandbox, JNI, native libraries,
  resources, renderer lifecycle and security updates.
- Verify APK assembly in Android Gradle CI for every commit; on device, verify loaded native engine is Chromium,
  `auto` selects it, missing/failed Chromium falls back, and manual WebView mode works.
- Test with TV remote and USB keyboard/mouse across Android 4.4, 5.x, 7.x, 9 and 10+; assess
  legacy website support honestly. Test TLS refusal, DRM limitations, downloads and video.
- Add instrumentation tests, native crash tests, performance/memory tests and release signing.
