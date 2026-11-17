# Native Chromium SDK artifact

Do not copy a regular Chrome, Chromium, Cromite, Trichrome or WebView APK here: these are **not** embeddable AARs.

`chromium-engine.aar` must come from a maintained Chromium `//content` embedder port and contain
`io.rudycity.tvbrowser.chromium.EmbeddedChromiumProvider`, native `.so` libraries for the
supported ABIs, and the complete renderer/runtime resources and manifest services.

Without that artifact, the `chromium` flavor is disabled and the legacy APK uses System WebView.
See `docs/CHROMIUM_ENGINE.md` for the implementation contract and production gates.
