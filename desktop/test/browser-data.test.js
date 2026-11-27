import test from "node:test";
import assert from "node:assert/strict";
import {
  DEFAULT_SETTINGS,
  addBookmark,
  clearDownloads,
  clearHistory,
  clearSession,
  getBookmarks,
  getDownloads,
  getHistory,
  getSession,
  getSettings,
  isBookmarked,
  normalizeSession,
  normalizeSettings,
  normalizeStoredUrl,
  recordDownload,
  recordVisit,
  removeBookmark,
  saveSession,
  updateHistoryTitle,
  updateSettings
} from "../src/browser-data.js";

function createStorage(initial = {}) {
  const values = new Map(Object.entries(initial));
  return {
    getItem(key) {
      return values.has(key) ? values.get(key) : null;
    },
    setItem(key, value) {
      values.set(key, String(value));
    },
    removeItem(key) {
      values.delete(key);
    }
  };
}

test("stored URLs accept only HTTP and HTTPS", () => {
  assert.equal(normalizeStoredUrl("https://example.com/path"), "https://example.com/path");
  assert.equal(normalizeStoredUrl("http://localhost:3000"), "http://localhost:3000/");
  assert.equal(normalizeStoredUrl("javascript:alert(1)"), null);
  assert.equal(normalizeStoredUrl("file:///etc/passwd"), null);
  assert.equal(normalizeStoredUrl("not a URL"), null);
});

test("bookmarks are persistent, unique by URL, and removable", () => {
  const storage = createStorage();

  assert.equal(addBookmark("https://example.com", "Example", storage, 100), true);
  assert.equal(addBookmark("https://example.com/", "Updated title", storage, 200), true);
  assert.equal(getBookmarks(storage).length, 1);
  assert.equal(getBookmarks(storage)[0].title, "Updated title");
  assert.equal(getBookmarks(storage)[0].createdAt, 100);
  assert.equal(isBookmarked("https://example.com/", storage), true);
  assert.equal(removeBookmark("https://example.com", storage), true);
  assert.equal(isBookmarked("https://example.com", storage), false);
});

test("bookmark writes reject non-web destinations", () => {
  const storage = createStorage();
  assert.equal(addBookmark("javascript:alert(1)", "Unsafe", storage), false);
  assert.deepEqual(getBookmarks(storage), []);
});

test("history records visits, counts repeat visits, and updates titles", () => {
  const storage = createStorage();

  assert.equal(recordVisit("https://example.com", "Example", storage, 100), true);
  assert.equal(recordVisit("https://example.com/", "Example home", storage, 200), true);
  assert.equal(updateHistoryTitle("https://example.com", "Final title", storage), true);

  const history = getHistory(storage);
  assert.equal(history.length, 1);
  assert.equal(history[0].visits, 2);
  assert.equal(history[0].visitedAt, 200);
  assert.equal(history[0].title, "Final title");
});

test("history stores only safe web URLs and can be cleared", () => {
  const storage = createStorage();

  assert.equal(recordVisit("file:///private.txt", "Private file", storage), false);
  assert.equal(getHistory(storage).length, 0);
  recordVisit("https://example.com", "Example", storage, 100);
  assert.equal(clearHistory(storage), true);
  assert.deepEqual(getHistory(storage), []);
});

test("download activity records completion status and can be cleared", () => {
  const storage = createStorage();

  assert.equal(recordDownload({
    url: "https://example.com/report.pdf",
    path: "/downloads/report.pdf",
    success: true
  }, storage, 500), true);
  assert.equal(recordDownload({
    url: "https://example.com/broken.zip",
    path: "",
    success: false
  }, storage, 600), true);

  const downloads = getDownloads(storage);
  assert.equal(downloads.length, 2);
  assert.equal(downloads[0].success, false);
  assert.equal(downloads[1].path, "/downloads/report.pdf");
  assert.equal(clearDownloads(storage), true);
  assert.deepEqual(getDownloads(storage), []);
});

test("malformed stored collections recover as empty collections", () => {
  const storage = createStorage({
    "rudy.browser.desktop.bookmarks.v1": "{broken",
    "rudy.browser.desktop.history.v1": "null",
    "rudy.browser.desktop.downloads.v1": "42"
  });

  assert.deepEqual(getBookmarks(storage), []);
  assert.deepEqual(getHistory(storage), []);
  assert.deepEqual(getDownloads(storage), []);
});

test("settings default to the dark theme with Google search", () => {
  const storage = createStorage();
  const settings = getSettings(storage);

  assert.equal(settings.theme, "dark");
  assert.equal(settings.searchEngine, "google");
  assert.equal(settings.defaultZoom, 1);
  assert.equal(settings.startupUrl, "");
  assert.deepEqual(settings.permissions, {});
  assert.equal(DEFAULT_SETTINGS.theme, "dark");
});

test("settings are validated before they are stored", () => {
  const storage = createStorage();

  const applied = updateSettings({
    theme: "light",
    searchEngine: "duckduckgo",
    startupUrl: "https://example.com/start",
    defaultZoom: 1.25
  }, storage);

  assert.equal(applied.theme, "light");
  assert.equal(applied.searchEngine, "duckduckgo");
  assert.equal(applied.startupUrl, "https://example.com/start");
  assert.equal(applied.defaultZoom, 1.25);
  assert.equal(getSettings(storage).theme, "light");
});

test("settings reject unsupported themes, schemes, and zoom values", () => {
  const settings = normalizeSettings({
    theme: "neon",
    searchEngine: "yandex",
    startupUrl: "file:///etc/passwd",
    defaultZoom: 99,
    permissions: {
      "https://example.com": ["microphone", "not a kind", 42],
      "file:///tmp": ["camera"],
      "javascript:alert(1)": ["camera"]
    }
  });

  assert.equal(settings.theme, "dark");
  assert.equal(settings.searchEngine, "google");
  assert.equal(settings.startupUrl, "");
  assert.equal(settings.defaultZoom, 3);
  assert.deepEqual(settings.permissions, { "https://example.com": ["microphone"] });
});

test("permission origins are reduced to a bare origin", () => {
  const settings = normalizeSettings({
    permissions: { "https://example.com:8443/path?q=1": ["camera"] }
  });

  assert.deepEqual(settings.permissions, { "https://example.com:8443": ["camera"] });
});

test("session restore keeps only web URLs and never private tabs", () => {
  const storage = createStorage();

  assert.equal(saveSession([
    { url: "https://example.com/one", private: false },
    { url: "https://example.com/two", private: true },
    { url: "javascript:alert(1)" }
  ], 1, storage), true);

  const session = getSession(storage);
  assert.deepEqual(session.tabs, [{ url: "https://example.com/one", private: false }]);
  assert.equal(session.activeIndex, 0);
});

test("session normalization clamps the active index", () => {
  assert.deepEqual(normalizeSession({ tabs: [], activeIndex: 5 }), { tabs: [], activeIndex: 0 });
  assert.deepEqual(
    normalizeSession({ tabs: [{ url: "https://a.example" }, { url: "https://b.example" }], activeIndex: 9 }),
    { tabs: [{ url: "https://a.example/", private: false }, { url: "https://b.example/", private: false }], activeIndex: 1 }
  );
});

test("clearing the session leaves nothing to restore", () => {
  const storage = createStorage();

  saveSession([{ url: "https://example.com" }], 0, storage);
  assert.equal(clearSession(storage), true);
  assert.deepEqual(getSession(storage), { tabs: [], activeIndex: 0 });
});
