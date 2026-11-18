# Rudy Browser — Gecko APK size optimization

Date: 2026-11-18

## Evidence

The immutable [0.8.0 beta release](https://github.com/RudyCity/tv-browser/releases/tag/v0.8.0-beta.db0f0e71) shipped a multi-ABI GeckoView debug APK of **547,515,209 bytes**.

The first successful split build on source commit `2566d86b225b5e4d0da68a36b56d7ccb870bfd05` produced:

| CPU ABI | APK filename suffix | APK bytes | Decimal MB |
|---|---|---:|---:|
| arm64-v8a | `arm64-v8a-debug.apk` | 212,953,639 | 213.0 |
| armeabi-v7a | `armeabi-v7a-debug.apk` | 173,744,971 | 173.7 |
| x86_64 | `x86_64-debug.apk` | 232,497,821 | 232.5 |

Evidence: [GitHub Actions run 44266897359](https://github.com/RudyCity/tv-browser/actions/runs/44266897359), packaging/unit tests and signed APK checks passed in job `gecko-build`; [download three debug APKs as an Actions ZIP](https://github.com/RudyCity/tv-browser/actions/runs/44266897359/artifacts/14410463019). **This is a temporary CI artifact, not a new GitHub Release.** If the Gecko emulator job is still in progress, do not claim the whole workflow passed.

## Build procedure

```sh
gradle -p gecko-build --no-daemon -PrudyGeckoAbiSplits=true :app:testGeckoDebugUnitTest :app:assembleGeckoDebug :engine-gecko:assembleDebug
python3 -m unittest discover -s scripts/tests -p test_gecko_abi_packaging.py
python3 scripts/package_gecko_abis.py --input app/build/outputs/apk/gecko/debug --output dist/gecko
```

- The `-PrudyGeckoAbiSplits=true` flag is opt-in, so existing legacy/preview builds are unaffected.
- Production-like Gecko functionality (Firefox native renderer, WebExtensions and permissions) is preserved; only unrelated native CPU libraries are removed from each APK.
- The `gecko-build` job now validates actual native `.so` ABI paths, APK signing, Android package identity, target SDK and checksum before publishing a temporary artifact.
- The independent Gecko API29 x86_64 emulator build still uses the original universal debug packaging, so emulator instrumentation does not lose its existing install path.
- A future manual release job downloads the three ABI artifacts and checks that each contains only the expected ABI; it never overwrites an immutable prior release.

## Choosing the right APK

Use the **CPU architecture**, not Android version, to select an APK.

- `arm64-v8a`: 64-bit ARM Android 8.0+ phones, tablets and TV boxes.
- `armeabi-v7a`: 32-bit ARM Android 8.0+ devices.
- `x86_64`: supported x86-64 devices and emulators.

Check `adb shell getprop ro.product.cpu.abilist` to confirm the device's supported ABIs. Only install one Gecko variant on a device, since these variants share one application ID.

All artifacts are **debug-signed**, have no stable upgrade-signing guarantee, and are not certified for physical TV playback, DRM, production security or Google Play. The existing 0.8.0 beta release remains unchanged. For Play distribution, use a properly signed Android App Bundle, not multiple debug APKs.

## Next steps

Measure a production-like APK with R8 and resource shrinking under device tests. Native GeckoView libraries, JNI and dynamic reflection must not be stripped speculatively; library compression and release packaging each require dedicated startup, crash, memory and media regression tests. Physical-device runtime verification is still outstanding.
