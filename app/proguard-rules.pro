# Default no-shrink build keeps debugging simpler for legacy device compatibility.

# Modern Compose toolbar is loaded by name only; preserve the reflection boundary.
-keep class io.rudycity.tvbrowser.compose.ComposeBrowserToolbar { public <init>(...); }
