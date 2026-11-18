# M2 — Cleartext traffic policy (first security slice)

Rudy Browser currently has two different device compatibility goals:

- `legacy`: Android 4.4+ / older TVs, where some local firmware pages may use
  HTTP. The app intentionally keeps cleartext available and warns about plain
  HTTP popups/downloads. **This build is less secure by design.**
- `preview`: API 21+ Compose user interface; unencrypted HTTP connections are
  disallowed. Modern Chromium (if ever embedded) uses the same policy.

The policy is applied at multiple layers:

1. Android application manifest uses per-flavor `usesCleartextTraffic`.
2. The UI refuses direct http:// input, web-intent links, insecure popup
   approval and HTTP downloads in modern builds.
3. WebView rejects new HTTP page navigation and HTTP redirects; request
   interception returns an empty response for an HTTP main frame, even on old
   API 21–25 devices where manifest enforcement is not reliable.
4. Mixed-content settings already deny insecure subresources on supported
   WebViews, TLS certificate errors are cancelled, and file/content access
   remains restricted.

This does not assert universal enforcement for all network APIs or third
party libraries, and not all ancient WebViews behave identically. HTTP
websites that must work on older TVs should use the legacy APK.

**Automated gates:**

- Pure Java policy JUnit and smoke tests.
- Build both APK flavors and inspect the actual compiled Android manifest
  using Android SDK `aapt` for cleartext true on legacy and false on Preview.
- In the API 29 emulator, assert Android NetworkSecurityPolicy rejects
  cleartext for the modern Preview, alongside the existing Compose startup
  and progress checks.

**Remaining M2:**

- Migrate to a current Google Play target SDK only after behavior/permissions
  audit and real device testing. This build still targets SDK 28.
- Permanent, secure production signing that supports upgrades and never leaks
  private keys. Current debug artifacts rotate signing keys.
- Instrument additional HTTP redirect/download tests on API 21–25 and a
  physical TV. Review security posture before any public release.

Android documentation:
- https://developer.android.com/privacy-and-security/security-config
- https://developer.android.com/guide/topics/manifest/application-element
