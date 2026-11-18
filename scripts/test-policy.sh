#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
temp_dir="$(mktemp -d)"
trap 'rm -rf "$temp_dir"' EXIT
javac -d "$temp_dir" \
  app/src/main/java/io/rudycity/tvbrowser/EnginePolicy.java \
  app/src/main/java/io/rudycity/tvbrowser/UrlTools.java \
  app/src/main/java/io/rudycity/tvbrowser/PopupPolicy.java \
  app/src/main/java/io/rudycity/tvbrowser/ExtensionPolicy.java \
  app/src/main/java/io/rudycity/tvbrowser/CursorPolicy.java \
  app/src/main/java/io/rudycity/tvbrowser/VideoTools.java \
  app/src/main/java/io/rudycity/tvbrowser/BrowserLayoutPolicy.java \
  app/src/main/java/io/rudycity/tvbrowser/BrowserPresentationPolicy.java \
  app/src/main/java/io/rudycity/tvbrowser/BrowserNetworkPolicy.java
cat > "$temp_dir/EngineSmokeTest.java" <<'JAVA'
import io.rudycity.tvbrowser.EnginePolicy;
import io.rudycity.tvbrowser.UrlTools;
import io.rudycity.tvbrowser.ExtensionPolicy;
import io.rudycity.tvbrowser.PopupPolicy;
import io.rudycity.tvbrowser.CursorPolicy;
import io.rudycity.tvbrowser.VideoTools;
import io.rudycity.tvbrowser.BrowserLayoutPolicy;
import io.rudycity.tvbrowser.BrowserPresentationPolicy;
import io.rudycity.tvbrowser.BrowserNetworkPolicy;
public class EngineSmokeTest {
    static int n;
    static void check(boolean result) { n++; if (!result) throw new AssertionError("Check #" + n); }
    public static void main(String[] args) {
        check(!EnginePolicy.tryChromium(19, "auto", true));
        check(!EnginePolicy.tryChromium(28, "chromium", true));
        check(EnginePolicy.tryChromium(29, "auto", true));
        check(EnginePolicy.tryChromium(35, "chromium", true));
        check(!EnginePolicy.tryChromium(35, "auto", false));
        check(!EnginePolicy.tryChromium(35, "webview", true));
        check(EnginePolicy.sanitize("invalid").equals("auto"));
        check(EnginePolicy.sanitize(null).equals("auto"));
        check(EnginePolicy.fallbackReason(28, "auto", true).contains("Android 10"));
        check(EnginePolicy.fallbackReason(35, "auto", false).contains("not packaged"));
        check(UrlTools.navigate("example.com").equals("https://example.com"));
        check(UrlTools.navigate("hi tv").contains("hi+tv"));
        check(UrlTools.isWebUrl("https://example.com"));
        check(!UrlTools.isWebUrl("file:///tmp/a"));
        check(!UrlTools.isWebUrl("https://user:password@example.com"));
        check(ExtensionPolicy.host("HTTPS://Example.com/a").equals("example.com"));
        check(ExtensionPolicy.host("file:///tmp/a") == null);
        check(ExtensionPolicy.host("https://example.com.").equals("example.com"));
        check(ExtensionPolicy.host("https://user:password@example.com") == null);
        check(ExtensionPolicy.shouldBlockResource("https://mysite.net",
                "https://x.google-analytics.com/collect", false));
        check(!ExtensionPolicy.shouldBlockResource("https://mysite.net",
                "https://google-analytics.com.evil.net/collect", false));
        check(!ExtensionPolicy.shouldBlockResource("https://mysite.net",
                "https://doubleclick.net/", true));
        check(ExtensionPolicy.userscriptOrigin("https://example.com"));
        check(ExtensionPolicy.knownId(ExtensionPolicy.VIDEO_TOOLS));
        check(ExtensionPolicy.IDS.length == 5);
        check(!ExtensionPolicy.userscriptOrigin("http://example.com"));
        check(CursorPolicy.clamp(-100,100,12) == 12);
        check(CursorPolicy.move(80,1,40,100,12) == 88);
        check(CursorPolicy.move(20,-1,40,100,12) == 12);
        check(VideoTools.javascript(VideoTools.PLAY_PAUSE) != null);
        check(VideoTools.javascript("alert(1)") == null);
        check(PopupPolicy.isPreviewable("https://example.com/link"));
        check(!PopupPolicy.isPreviewable("intent://launch/#Intent;end"));
        check(!PopupPolicy.isPreviewable("https://example.com/\nattack"));
        check(PopupPolicy.mightInstallSoftware("https://cdn.example.com/app.apk?download=1"));
        check(!PopupPolicy.mightInstallSoftware("https://example.com/article"));
        check(PopupPolicy.usesPlainHttp("http://legacy.local/"));
        check(BrowserLayoutPolicy.toolbarMode(320, false) == BrowserLayoutPolicy.MOBILE);
        check(BrowserLayoutPolicy.toolbarMode(390, false) == BrowserLayoutPolicy.MOBILE);
        check(BrowserLayoutPolicy.toolbarMode(600, false) == BrowserLayoutPolicy.TABLET);
        check(BrowserLayoutPolicy.toolbarMode(768, false) == BrowserLayoutPolicy.TABLET);
        check(BrowserLayoutPolicy.toolbarMode(840, false) == BrowserLayoutPolicy.TABLET);
        check(BrowserLayoutPolicy.toolbarMode(768, true) == BrowserLayoutPolicy.TV_COMPACT);
        check(BrowserLayoutPolicy.toolbarMode(1280, true) == BrowserLayoutPolicy.TV_WIDE);
        check(!BrowserLayoutPolicy.hasSecondRow(BrowserLayoutPolicy.MOBILE));
        check(!BrowserLayoutPolicy.hasStatusFooter(BrowserLayoutPolicy.TABLET));
        check(BrowserLayoutPolicy.hasSecondRow(BrowserLayoutPolicy.TV_COMPACT));
        check(!BrowserPresentationPolicy.showJavaProgress(true, true));
        check(BrowserPresentationPolicy.showJavaProgress(false, true));
        check(!BrowserPresentationPolicy.showJavaProgress(false, false));
        check(!BrowserPresentationPolicy.shouldTryCompose(false, 19, true));
        check(!BrowserPresentationPolicy.shouldTryCompose(true, 35, true));
        check(!BrowserPresentationPolicy.shouldTryCompose(false, 35, false));
        check(BrowserPresentationPolicy.shouldTryCompose(false, 21, true));
        check(!BrowserNetworkPolicy.allowHttpForFlavor("preview"));
        check(BrowserNetworkPolicy.allowHttpForFlavor("legacy"));
        check(BrowserNetworkPolicy.blocksCleartext("http://example.com/", false));
        check(!BrowserNetworkPolicy.blocksCleartext("https://example.com/", false));
        check(!BrowserNetworkPolicy.blocksCleartext("http://example.com/", true));
        check(!BrowserLayoutPolicy.isTelevision(false, false, false, true));
        check(BrowserLayoutPolicy.isTelevision(true, false, false, true));
        check(BrowserLayoutPolicy.isTelevision(false, false, false, false));
        System.out.println("PASS " + n + " Java policy, form-factor, popup, add-on and URL smoke assertions");
    }
}
JAVA
javac -cp "$temp_dir" -d "$temp_dir" "$temp_dir/EngineSmokeTest.java"
java -cp "$temp_dir" EngineSmokeTest
