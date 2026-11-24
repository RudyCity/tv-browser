import test from "node:test";
import assert from "node:assert/strict";
import { normalizeDestination, normalizeNavigationState } from "../src/navigation.js";

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
