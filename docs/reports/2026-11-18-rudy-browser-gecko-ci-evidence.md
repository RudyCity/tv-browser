# GeckoView rebuild — GitHub evidence 2026-11-18

Repo: `RudyCity/tv-browser`, branch `main`; audit baseline `6d48497cb832a2d7f417e1abd58302124f926518`. No new branch, force push, production signing, or publish operation.

| Commit | Changes | CI result | Runtime proof |
| --- | --- | --- | --- |
| `afb4115d` | New full master plan | Android APK run 44237681558 success | not applicable |
| `6e492153` | Engine policy/factory | Android APK 44237951415 success; Compose emulator 44237951460 success | Preview WebView only |
| `44d288d9` | New Gecko AAR flavor, independent session/view | Android APK 44238228470 success; Gecko build failed AAR metadata | none for Gecko |
| `4d52f90d` | AGP9/SDK37 modern root | Android APK 44238699072 success; Gecko failed SDK discovery | none for Gecko |
| `72b57414`, `73403cda` | SDK preview channel and exact platform ID | Gecko failed until using `platforms;android-37.0` | none |
| `b2802639` | Separate built-in Kotlin Compose build | Gecko build+unit job success in https://github.com/RudyCity/tv-browser/actions/runs/44239262264 | API29 job still in progress at last check |
| `b2186847` | Real Gecko WebExtension API and signed XPI consent | pending exact-SHA CI | none |
| `09990a5b` | First-contentful-paint and extension instrumentation tests | pending exact-SHA CI | none |

## Open risks

- Exact Gecko API159 runtime behavior and Android 29 physical rendering remain unconfirmed.
- WebExtensions prompt API and original test extension have no passing emulator proof yet.
- Fullscreen video, Gecko site permissions, engine-specific data clear correctness, actual tab/session store, TV D-pad and hardware testing, Play target SDK/signing remain incomplete.
- The original legacy/preview Android build path must remain green; modern Gecko is built from `gecko-build` with Kotlin built into AGP9.
- A green APK compile does not prove the GeckoView surface rendered HTTPS or that a Firefox extension ran.
