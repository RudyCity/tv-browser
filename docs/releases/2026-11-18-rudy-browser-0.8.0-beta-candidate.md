# Rudy Browser 0.8.0 Beta — tested download candidate

**Build source:** `29d186a71ce2af9bcadad148a9dea3ac3f60a239` on `main` · `versionCode 12` · `versionName 0.8.0`.

**Release publication status: NOT PUBLISHED.** The exact-SHA debug APKs passed four GitHub CI workflows and were assembled for a pre-release, but [publication workflow 44254161362](https://github.com/RudyCity/tv-browser/actions/runs/44254161362) failed at GitHub's Releases API with **HTTP 403: Resource not accessible by integration**. Do not imply the Release page exists.

## Download exact-SHA CI artifacts (ZIP, temporary / retained 14 days)

| Android variant | GitHub Actions artifact | Android compatibility |
| --- | --- | --- |
| GeckoView | [rudy-browser-gecko-debug-apk](https://github.com/RudyCity/tv-browser/actions/runs/44253354640/artifacts/14404229895) | Android 8.0+ / API26+, full Mozilla GeckoView |
| Compose Preview | [rudy-browser-preview-installable-apk](https://github.com/RudyCity/tv-browser/actions/runs/44253354713/artifacts/14405480019) | Android 5.0+ / API21+, System WebView |
| Legacy TV | [rudy-browser-legacy-debug-apk](https://github.com/RudyCity/tv-browser/actions/runs/44253354713/artifacts/14404981201) | Android 4.4+ / API19+, System WebView |

Each Actions link downloads a **ZIP containing the APK**, not a direct APK like GitHub Releases. Log into GitHub with repo access, extract the ZIP, and install the enclosed APK. These assets expire after their Actions retention interval. All are **debug signed**; do not rely on upgrade-stable signing.

### SHA-256 of APK files, from the verified artifact staging logs

```text
f0b843a41d655a1f2af6e3366c07a0da93a3faae0f569cf827f2f3e050ad9eda  RudyBrowser-GeckoView-Android8-debug.apk
c13cdc8dade2616bc4a590ea5241ef72cf2a3c07d9b044ad87de72e773f2c2f5  RudyBrowser-Legacy-Android4-debug.apk
448e00d16cab615261269ca7d5c039f23e7efc2945b36261e20243a7be47f25a  RudyBrowser-Preview-Android5-debug.apk
```

APK names above reflect the release staging step; the ZIPs contain their original build filenames (`app-gecko-debug.apk`, `app-legacy-debug.apk`, `app-preview-debug.apk`). Verify the extracted APK hash before installation.

## Four passed checks on the same source SHA

- [Legacy and Compose Android APK build + unit tests](https://github.com/RudyCity/tv-browser/actions/runs/44253354713) — success
- [Real GeckoView build and Android API29 emulator](https://github.com/RudyCity/tv-browser/actions/runs/44253354640) — success
- [Compose Android API29 emulator](https://github.com/RudyCity/tv-browser/actions/runs/44253354696) — success
- [Android TV API29 emulator, D-pad and launcher](https://github.com/RudyCity/tv-browser/actions/runs/44253354737) — success

## Safe publication recovery

`Publish Rudy Browser developer pre-release` is now **manual-only** and requires four successful exact-SHA run IDs. It never replaces existing release assets. Grant GitHub Actions `Contents: write` if the owner wishes to use the built-in token, or configure a narrowly scoped `RUDY_RELEASE_TOKEN` secret with repository Contents write access; never commit tokens. The owner can then explicitly re-run the manual release workflow with this SHA and the four run IDs listed above. This is not an instruction to weaken branch or repository security policy.

Production signing keys, physical Android phone/TV runtime tests, Play compliance, standalone native Chromium/Chrome extension build and full security/performance certification remain incomplete. The previously published `v0.7.1-preview.67001288` developer pre-release remains available separately.
