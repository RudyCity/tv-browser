# Embedded Chromium design and acceptance gates

**Decision:** Prefer a genuine, in-app Chromium renderer on supported devices; if it is unavailable,
unsupported, fails initialization, or crashes, use the device's Android System WebView. Do **not**
claim WebView, an external Chrome `ACTION_VIEW`, Trichrome provider, or a simple user-agent override
is an embedded Chromium engine.

## What actually ships now

- `legacy` variant (**Android API 19+**): functional system-WebView browser with engine routing,
  remote-first UI, and a settings menu. No modern Chromium binary is bundled.
- `chromium` variant (**Android API 29+**): only enabled when a real `app/libs/chromium-engine.aar`
  is supplied. The native Chromium renderer is **not present in this source archive**.
- `auto` preference first tries the embedded provider and safely falls back to WebView. For API
  19–28, skip the modern Chromium loader entirely. `webview` forces the system renderer; selecting
  `chromium` on unsupported devices reports a fallback, never silently claims it was activated.
- Engine status is visible in the footer and **Menu → Engine / About**.

As confirmed by a Chromium WebView maintainer in January 2026, Chromium does not
ship a supported reusable Content Shell or Chromium AAR for arbitrary Android apps.
The viable custom paths are building/maintaining an Android //content-based embedder
within Chromium's GN build, or designing an unsupported distribution AAR target
such as the upstream dist_aar template. Each requires the app-specific native
renderer, sandbox/service packaging, bridge APIs, security update process, and
continuous compatibility tests. CEF does not support Android. The Content Shell
APK is a test utility, not a production browser runtime.

A modern Android Chromium build is not a drop-in Java Maven dependency. It is a Chromium-native
multi-process engine requiring Java/native glue, content/embedder resources, ABI-specific binaries,
renderer services, sandboxing, update maintenance, and substantial build infrastructure. An AAR
containing only a `WebView` wrapper is not acceptable.

## Upstream starting point and build flow

1. Build and pin Chromium using the official Linux checkout instructions:
   https://chromium.googlesource.com/chromium/src/+/main/docs/android_build_instructions.md
2. Study Chromium's `//content/shell/android` and `content_shell_apk` build targets as a
   **development starting point**, **not a production-ready embeddable SDK**. `content_shell_apk`
   is a standalone test APK, not a reusable AAR. It cannot simply be renamed to `chromium-engine.aar`.
3. Implement a maintained `//content`-based Android embedder and a packaging target that produces
   `chromium-engine.aar` with renderer services, JNI `.so` libraries for supported ABIs, resources,
   data files, and appropriate manifest entries. Build `engine-api` separately; the embedder uses
   `compileOnly` against this interface. Include all required native runtime dependencies and
   production sandbox/isolation policy, with ABI and min-SDK validation.
4. Export a public Java provider class named
   `io.rudycity.tvbrowser.chromium.EmbeddedChromiumProvider` with constructor
   `(android.app.Activity, io.rudycity.tvbrowser.BrowserEngine.Listener, boolean desktop)`.
   This class **implements** the interface `io.rudycity.tvbrowser.BrowserEngine` from `:engine-api`.
   The provider's `name()` must return exactly `Embedded Chromium`. Its `view()` must return a
   Chromium-owned native surface, NOT an `android.webkit.WebView`.
5. Map all `BrowserEngine` methods, including URL and history navigation, rendering lifecycle,
   fullscreen video, TV D-pad commands, user agent, native cookies/clear data, downloads, title,
   native engine crash detection, and HTTPS certificate validation, to the Chromium embedder.
   Invoke `Listener.onEngineCrashed` when recovery is required.
6. Verify the AAR has `classes.jar`, the specified provider class, native `.so` files, and all
   runtime resources; run `python3 scripts/check_chromium_aar.py app/libs/chromium-engine.aar`.
   This *structural* check does **not** prove the native engine is safe or functional.
7. Build the APK: `gradle testChromiumDebugUnitTest assembleChromiumDebug`. On physical Android 10+
   devices, inspect loaded native libraries, engine version, network behavior, HTTPS errors,
   sandboxing, video, D-pad, crash handling, and fallback. Do not release before this verification.
8. Keep Chromium patched when upstream releases security updates. Distribute per-ABI builds if a
   universal APK is too large. Never download unverified code into writable app paths at runtime.

## Compatibility and security boundaries

| Device OS | Modern Chromium variant | Legacy fallback |
| --- | --- | --- |
| Android 4.4–7.1 (API 19–25) | Not supported by current Chromium | System WebView; old engine limitations |
| Android 8–9 (API 26–28) | Not supported by current Chromium | System WebView; update provider if possible |
| Android 10+ (API 29+) | Possible with maintained, *actually embedded* Chromium build | System WebView on missing/crashed engine |

Do not install an outdated Crosswalk/Cef browser library as a security workaround; Crosswalk
is end-of-life. Modern Chromium doesn't provide a supported one-line embedded AAR or a viable
safe backport for the oldest Android TV firmware. **Fallback ensures app startup, not that
modern websites/DRM/codecs will work on old televisions.**

## Runtime QA gate

- Confirm `BrowserEngine.name()` reports *Embedded Chromium* only when its independent native
  library has actually loaded and rendered a page; package inspection alone is insufficient.
- Test forced Chromium / forced WebView / auto on API 19, 23, 28, 29, 34; TV remotes and Android TV
  launcher behavior on supported hardware.
- Simulate missing provider, JNI linkage error, native initialization exception, renderer crash,
  offline network, invalid TLS certificate, and return from fullscreen.
- Verify history/bookmarks persist across engine switching but renderer-specific cookies and cache
  are cleared only in the active renderer (no false cross-engine cookie-sharing assumption).
- Test memory and startup latency on 1 GB and 2 GB RAM TV hardware; monitor native renderer
  crashes and resource usage before a signed release.

## Technical references

- Chromium Android build: https://chromium.googlesource.com/chromium/src/+/main/docs/android_build_instructions.md
- Chromium supported platforms: https://chromium.googlesource.com/chromium/src/+/HEAD/docs/supported_platforms.md
- Chromium maintainers on the embedder problem: https://groups.google.com/a/chromium.org/g/chromium-dev/c/7oa3OoEt-7w
- Android WebView architecture: https://chromium.googlesource.com/chromium/src/+/main/android_webview/docs/architecture.md
