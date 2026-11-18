# Rudy Browser — Independent Chromium and Chrome Extensions

**Status: source-build pipeline implemented; native APK not yet built or runtime-tested.**
The existing Rudy Browser APK uses Android System WebView. Chromium builds a
different APK, NOT an embeddable AAR, so there is no seamless single-APK fallback.

## Source, compatibility and licensing

This recipe builds [Titanium Browser for Android](https://github.com/jqssun/android-titanium-browser),
an open-source Chromium/GrapheneOS Vanadium-based Android browser with Chrome extension
support. The code is third-party and GPL-2.0 licensed. Distributing derived binaries
requires the corresponding source, attribution and applicable third-party notices.

The source lock pins Chromium 157.0.8103.78 at Titanium commit
bde250ae7c7d43a7a1827a44d07841132f6db7f4 and Vanadium commit
a8b304ef63b51f0158775a9d4f90106e8fc9a721. A Titanium helper CRX at
release v0.0.1 is checked against its exact SHA256 and used in place of
the upstream mutable /latest hook.

* Native build target: Android 10+ (API 29+), ARM64 preferred.
* Native Chromium extension support comes from upstream Titanium patches:
  Chrome Web Store and chrome://extensions, with compatibility limitations.
* Package ID changes to io.rudycity.tvbrowser.chromium.
* Native manifest advertises Android TV Leanback launch and no touchscreen
  requirement; its UI is not yet optimized for a television remote.
* Android 4.4–9 remains on the existing WebView app and its built-in add-ons.

## Building

A dedicated Linux x64 builder needs 120 GiB free SSD, 16 GiB RAM minimum
(32+ GiB preferred), Python 3, Git, JDK keytool, sudo and apt. The upstream
build script installs dependencies and clones a huge Chromium source tree.
Never run on a production machine or in an unrelated dirty checkout.

Source-only checks:

    python3 native-chromium/build_native.py --check
    python3 -m unittest discover -s native-chromium/tests -v

Native build in new isolated directory:

    python3 native-chromium/build_native.py --workdir /data/chromium-tv-new \
      --output /data/chromium-tv-artifacts

GitHub Actions workflow chromium-native.yml tests source patches on push.
The giant native compilation job is **manual only**, and requires a
self-hosted Linux x64 runner labeled chromium-builder. It creates an
ephemeral debug signing key, never touches production secrets, and does not
publish a release. It uploads tv-browser-chromium-arm64-debug.apk and
SHA256SUMS only after package, APK ZIP, native library and TV intent checks.

## Verification not yet done

Compilation and a physical Android TV runtime test are still required.
Test loaded native Chromium libraries, certificates, DRM/video, memory,
TV D-pad focus, chrome://extensions, MV3 extension installation/permissions,
content-script execution, persistence, and uninstall. Some desktop Chrome
extensions will not run on Android. Do not claim any extensions work merely
because the APK builds or a menu appears.

A separate APK cannot implement the previous BrowserEngine embedder contract.
The WebView APK remains a usable fallback installed independently, without
automatic in-process recovery from a native Chromium crash. One-APK switching
requires separate Chromium embedder/JNI work and further security testing.


## Additional safety gates (0.8.0 beta source)

The native build verifier now requires a structurally valid ARM64 ELF64 AArch64 shared library, a native APK with Android 10+ minimum, package ID and Android TV launcher checks, and a valid APK signing certificate. It hashes the APK with a bounded-memory SHA-256 reader and emits `build-provenance.json` recording locked upstream/Vanadium commits and the bundled extension digest.

The manual `chromium-builder` runner and actual upstream native compilation remain **unavailable/unverified**, so this cannot be advertised as a working installable Chromium APK. A real signed ARM64 build, successful browser runtime, MV3 installation test and license/source review remain hard release gates.
