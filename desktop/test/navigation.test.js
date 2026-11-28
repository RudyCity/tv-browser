import test from "node:test";
import assert from "node:assert/strict";
import {
  buildSearchUrl,
  clampZoom,
  classifyNavigationError,
  describeSecurity,
  formatZoom,
  normalizeChromeHeight,
  normalizeDestination,
  normalizeNavigationState,
  normalizeSearchEngine,
  stepZoom
} from "../src/navigation.js";

test("preserves valid HTTP and HTTPS URLs", () => {
  assert.equal(normalizeDestination("https://example.com/path"), "https://example.com/path");
  assert.equal(normalizeDestination("http://example.com"), "http://example.com/");
});

test("adds HTTPS to a domain and path", () => {
  assert.equal(normalizeDestination("example.com/docs"), "https://example.com/docs");
});

test("keeps local development servers on HTTP", () => {
  assert.equal(normalizeDestination("localhost:3000/app"), "http://localhost:3000/app");
  assert.equal(normalizeDestination("127.0.0.1:8080"), "http://127.0.0.1:8080/");
});

test("turns words into an encoded search query", () => {
  assert.equal(normalizeDestination("rudy browser desktop"), "https://www.google.com/search?q=rudy%20browser%20desktop");
});

test("rejects unsafe and unsupported URL schemes", () => {
  assert.equal(normalizeDestination("javascript:alert(1)"), null);
  assert.equal(normalizeDestination("file:///etc/passwd"), null);
  assert.equal(normalizeDestination("ftp://example.com"), null);
});

test("rejects empty and malformed input", () => {
  assert.equal(normalizeDestination("   "), null);
  assert.equal(normalizeDestination(null), null);
  assert.equal(normalizeDestination("https://"), null);
});

test("normalizes native navigation availability without guessing", () => {
  assert.deepEqual(
    normalizeNavigationState({ canGoBack: true, canGoForward: false }),
    { canGoBack: true, canGoForward: false }
  );
  assert.deepEqual(
    normalizeNavigationState({ canGoBack: 1, canGoForward: "yes" }),
    { canGoBack: false, canGoForward: false }
  );
  assert.deepEqual(
    normalizeNavigationState(null),
    { canGoBack: false, canGoForward: false }
  );
});

test("search uses the selected engine and falls back to Google", () => {
  assert.equal(buildSearchUrl("rudy browser", "duckduckgo"), "https://duckduckgo.com/?q=rudy%20browser");
  assert.equal(buildSearchUrl("rudy browser", "bing"), "https://www.bing.com/search?q=rudy%20browser");
  assert.equal(buildSearchUrl("rudy browser", "unknown-engine"), "https://www.google.com/search?q=rudy%20browser");
  assert.equal(buildSearchUrl("   "), null);
});

test("unknown search engines normalize to the default", () => {
  assert.equal(normalizeSearchEngine("duckduckgo"), "duckduckgo");
  assert.equal(normalizeSearchEngine("  BING "), "bing");
  assert.equal(normalizeSearchEngine("yandex"), "google");
  assert.equal(normalizeSearchEngine(null), "google");
});

test("typed words use the requested engine", () => {
  assert.equal(
    normalizeDestination("rudy desktop", "bing"),
    "https://www.bing.com/search?q=rudy%20desktop"
  );
});

test("zoom is clamped to the supported range and formatted as a percentage", () => {
  assert.equal(clampZoom(1), 1);
  assert.equal(clampZoom(0.1), 0.5);
  assert.equal(clampZoom(9), 3);
  assert.equal(clampZoom("nonsense"), 1);
  assert.equal(clampZoom(1.254), 1.25);
  assert.equal(formatZoom(1.25), "125%");
  assert.equal(formatZoom(1), "100%");
});

test("zoom stepping moves through the discrete ladder", () => {
  assert.equal(stepZoom(1, "in"), 1.1);
  assert.equal(stepZoom(1, "out"), 0.9);
  assert.equal(stepZoom(3, "in"), 3);
  assert.equal(stepZoom(0.5, "out"), 0.5);
  assert.equal(stepZoom(1, "reset"), 1);
  assert.equal(stepZoom(1.05, "in"), 1.1);
  assert.equal(stepZoom(1.05, "out"), 1);
});

test("security indication reflects the real URL scheme", () => {
  assert.deepEqual(describeSecurity("https://example.com/path"), {
    label: "Connection is secure",
    secure: true,
    host: "example.com"
  });
  assert.deepEqual(describeSecurity("http://example.com"), {
    label: "Connection is not secure",
    secure: false,
    host: "example.com"
  });
  assert.equal(describeSecurity("about:blank").secure, false);
  assert.equal(describeSecurity("").label, "No page");
  assert.equal(describeSecurity("not a url").label, "Unknown address");
});

test("chrome height rejects unusable measurements", () => {
  assert.equal(normalizeChromeHeight(120), 120);
  assert.equal(normalizeChromeHeight("120.4"), 120);
  assert.equal(normalizeChromeHeight(0), 116);
  assert.equal(normalizeChromeHeight(-5), 116);
  assert.equal(normalizeChromeHeight(Number.NaN), 116);
  assert.equal(normalizeChromeHeight(9999), 400);
});

test("navigation error classification separates scheme rejections from runtime failures", () => {
  assert.deepEqual(
    classifyNavigationError("Only HTTP and HTTPS websites are allowed"),
    { key: "error.addressRejected", raw: "" }
  );
  assert.deepEqual(
    classifyNavigationError("Invalid address: empty host"),
    { key: "error.addressRejected", raw: "" }
  );
  assert.deepEqual(
    classifyNavigationError("Browser content view is not ready"),
    { key: null, raw: "Browser content view is not ready" }
  );
});
