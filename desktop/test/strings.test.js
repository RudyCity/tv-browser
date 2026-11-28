import test from "node:test";
import assert from "node:assert/strict";
import {
  DEFAULT_LANGUAGE,
  LANGUAGES,
  MESSAGES,
  listKeys,
  normalizeLanguage,
  translate
} from "../src/strings.js";

const REQUIRED_KEYS = [
  "toolbar.back",
  "toolbar.zoomReset",
  "settings.appearance",
  "settings.themeDark",
  "status.historyCleared",
  "status.downloaded",
  "error.addressRejected",
  "dialog.clearHistory"
];

test("exposes the supported languages and the default language", () => {
  assert.deepEqual(LANGUAGES, ["en", "id"]);
  assert.equal(DEFAULT_LANGUAGE, "en");
  assert.deepEqual(Object.keys(MESSAGES), ["en", "id"]);
});

test("every language carries the exact same key set", () => {
  assert.deepEqual(listKeys("en"), listKeys("id"));
  assert.equal(Object.keys(MESSAGES.en).length, Object.keys(MESSAGES.id).length);
  assert.equal(listKeys("en").length, Object.keys(MESSAGES.en).length);
});

test("every message is a non-empty string without an em dash", () => {
  for (const language of LANGUAGES) {
    for (const key of listKeys(language)) {
      const message = MESSAGES[language][key];
      assert.equal(typeof message, "string", key);
      assert.ok(message.trim().length > 0, key);
      assert.ok(!message.includes("\u2014"), key);
    }
  }
});

test("keys are stable, namespaced identifiers", () => {
  for (const key of listKeys("en")) {
    assert.match(key, /^[a-z][a-zA-Z0-9]*(\.[a-zA-Z][a-zA-Z0-9]*)+$/);
  }
});

test("covers the toolbar, settings, status, error, and dialog copy", () => {
  for (const key of REQUIRED_KEYS) {
    assert.ok(listKeys("en").includes(key), key);
    assert.ok(listKeys("id").includes(key), key);
  }
});

test("message stores are frozen so a stray assignment cannot replace copy", () => {
  assert.ok(Object.isFrozen(LANGUAGES));
  assert.ok(Object.isFrozen(MESSAGES));
  assert.ok(Object.isFrozen(MESSAGES.en));
  assert.ok(Object.isFrozen(MESSAGES.id));
});

test("unknown languages fall back to the default language", () => {
  assert.equal(translate("fr", "toolbar.back"), "Back");
  assert.equal(translate("de-DE", "status.ready"), "Ready");
  assert.equal(translate("", "toolbar.back"), "Back");
  assert.equal(translate(null, "error.retry"), "Try again");
  assert.equal(translate(42, "error.retry"), "Try again");
});

test("the requested language is used when it has the key", () => {
  assert.equal(translate("en", "toolbar.back"), "Back");
  assert.equal(translate("id", "toolbar.back"), "Kembali");
  assert.equal(translate("id", "settings.appearance"), "Tampilan");
  assert.equal(translate("id", "status.ready"), "Siap");
});

test("unknown keys come back as the key itself", () => {
  assert.equal(translate("en", "toolbar.nope"), "toolbar.nope");
  assert.equal(translate("id", "does.not.exist"), "does.not.exist");
  assert.equal(translate("fr", "does.not.exist"), "does.not.exist");
});

test("parameters are interpolated into the message", () => {
  assert.equal(
    translate("en", "status.downloaded", { path: "C:\\Users\\me\\file.zip" }),
    "Downloaded: C:\\Users\\me\\file.zip"
  );
  assert.equal(translate("en", "find.matches", { count: 3 }), "3 matches");
  assert.equal(translate("en", "find.matches", { count: 0 }), "0 matches");
  assert.equal(translate("en", "library.visits", { count: 2 }), "2 visits");
  assert.equal(translate("id", "status.zoomChanged", { percent: "125%" }), "Zoom 125%");
  assert.equal(translate("id", "status.downloaded", { path: "/tmp/a.zip" }), "Terunduh: /tmp/a.zip");
  assert.equal(translate("id", "status.themeSet", { theme: "Gelap" }), "Tema diatur ke Gelap");
});

test("placeholders without a value stay untouched", () => {
  assert.equal(translate("en", "status.downloaded"), "Downloaded: {path}");
  assert.equal(translate("en", "status.downloaded", {}), "Downloaded: {path}");
  assert.equal(translate("en", "status.downloaded", { path: null }), "Downloaded: {path}");
  assert.equal(translate("en", "status.downloaded", { path: undefined }), "Downloaded: {path}");
  assert.equal(translate("en", "status.downloaded", { other: "value" }), "Downloaded: {path}");
  assert.equal(translate("id", "permission.request", { origin: "https://example.com" }), "https://example.com meminta izin {kind}.");
});

test("interpolation ignores values inherited through the prototype", () => {
  assert.equal(translate("en", "status.downloaded", Object.create({ path: "/tmp/leak" })), "Downloaded: {path}");
});

test("normalizeLanguage accepts supported tags and rejects everything else", () => {
  assert.equal(normalizeLanguage("en"), "en");
  assert.equal(normalizeLanguage("id"), "id");
  assert.equal(normalizeLanguage("ID"), "id");
  assert.equal(normalizeLanguage("  id  "), "id");
  assert.equal(normalizeLanguage("En"), "en");
});

test("normalizeLanguage rejects null, numbers, objects, and foreign strings", () => {
  assert.equal(normalizeLanguage(null), "en");
  assert.equal(normalizeLanguage(undefined), "en");
  assert.equal(normalizeLanguage(42), "en");
  assert.equal(normalizeLanguage(Number.NaN), "en");
  assert.equal(normalizeLanguage({}), "en");
  assert.equal(normalizeLanguage([]), "en");
  assert.equal(normalizeLanguage(true), "en");
  assert.equal(normalizeLanguage(""), "en");
  assert.equal(normalizeLanguage("   "), "en");
  assert.equal(normalizeLanguage("fr"), "en");
  assert.equal(normalizeLanguage("english"), "en");
  assert.equal(normalizeLanguage("id-ID"), "en");
});

test("listKeys returns namespace keys in sorted order", () => {
  const keys = listKeys("en");
  assert.deepEqual(keys, [...keys].sort());
  assert.deepEqual(keys, keys.slice().sort((left, right) => (left < right ? -1 : left > right ? 1 : 0)));
  assert.deepEqual(listKeys("id"), keys);
  assert.deepEqual(listKeys("unknown-language"), keys);
  assert.ok(keys.length > 100);
});

test("listKeys returns a fresh array the caller cannot use to mutate the store", () => {
  const keys = listKeys("en");
  keys.push("toolbar.injected");
  keys.sort().reverse();
  assert.ok(!listKeys("en").includes("toolbar.injected"));
  assert.equal(listKeys("en").length, Object.keys(MESSAGES.en).length);
});
