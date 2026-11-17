#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
temp_dir="$(mktemp -d)"
trap 'rm -rf "$temp_dir"' EXIT
javac -d "$temp_dir" \
  app/src/main/java/io/rudycity/tvbrowser/EnginePolicy.java \
  app/src/main/java/io/rudycity/tvbrowser/UrlTools.java
cat > "$temp_dir/EngineSmokeTest.java" <<'JAVA'
import io.rudycity.tvbrowser.EnginePolicy;
import io.rudycity.tvbrowser.UrlTools;
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
        System.out.println("PASS " + n + " Java policy and URL smoke assertions");
    }
}
JAVA
javac -cp "$temp_dir" -d "$temp_dir" "$temp_dir/EngineSmokeTest.java"
java -cp "$temp_dir" EngineSmokeTest
