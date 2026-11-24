const STORAGE_KEYS = Object.freeze({
  bookmarks: "rudy.browser.desktop.bookmarks.v1",
  history: "rudy.browser.desktop.history.v1",
  downloads: "rudy.browser.desktop.downloads.v1"
});

const MAX_BOOKMARKS = 300;
const MAX_HISTORY = 500;
const MAX_DOWNLOADS = 150;

function getStorage(storage) {
  try {
    return storage ?? globalThis.localStorage ?? null;
  } catch {
    return null;
  }
}

function readCollection(key, storage) {
  const target = getStorage(storage);
  if (!target) return [];

  try {
    const value = JSON.parse(target.getItem(key) ?? "[]");
    return Array.isArray(value)
      ? value.filter(item => item && typeof item === "object" && !Array.isArray(item))
      : [];
  } catch {
    return [];
  }
}

function writeCollection(key, value, storage) {
  const target = getStorage(storage);
  if (!target) return false;

  try {
    target.setItem(key, JSON.stringify(value));
    return true;
  } catch {
    return false;
  }
}

export function normalizeStoredUrl(value) {
  if (typeof value !== "string") return null;

  try {
    const url = new URL(value);
    if (!["http:", "https:"].includes(url.protocol) || !url.hostname) return null;
    return url.href;
  } catch {
    return null;
  }
}

function safeTitle(title, url) {
  if (typeof title === "string" && title.trim()) return title.trim().slice(0, 300);

  try {
    return new URL(url).hostname;
  } catch {
    return url;
  }
}

export function getBookmarks(storage) {
  return readCollection(STORAGE_KEYS.bookmarks, storage)
    .filter(item => normalizeStoredUrl(item.url) !== null)
    .sort((left, right) => Number(right.createdAt || 0) - Number(left.createdAt || 0));
}

export function isBookmarked(url, storage) {
  const normalized = normalizeStoredUrl(url);
  return normalized !== null && getBookmarks(storage).some(item => item.url === normalized);
}

export function addBookmark(url, title, storage, createdAt = Date.now()) {
  const normalized = normalizeStoredUrl(url);
  if (!normalized) return false;

  const bookmarks = getBookmarks(storage);
  const existing = bookmarks.find(item => item.url === normalized);
  const previousCreatedAt = Number(existing?.createdAt);
  const next = {
    url: normalized,
    title: safeTitle(title, normalized),
    createdAt: Number.isFinite(previousCreatedAt) ? previousCreatedAt : createdAt,
    updatedAt: createdAt
  };

  const updated = [next, ...bookmarks.filter(item => item.url !== normalized)]
    .slice(0, MAX_BOOKMARKS);
  return writeCollection(STORAGE_KEYS.bookmarks, updated, storage);
}

export function removeBookmark(url, storage) {
  const normalized = normalizeStoredUrl(url);
  if (!normalized) return false;

  const bookmarks = getBookmarks(storage);
  return writeCollection(
    STORAGE_KEYS.bookmarks,
    bookmarks.filter(item => item.url !== normalized),
    storage
  );
}

export function getHistory(storage) {
  return readCollection(STORAGE_KEYS.history, storage)
    .filter(item => normalizeStoredUrl(item.url) !== null)
    .sort((left, right) => Number(right.visitedAt || 0) - Number(left.visitedAt || 0));
}

export function recordVisit(url, title, storage, visitedAt = Date.now()) {
  const normalized = normalizeStoredUrl(url);
  if (!normalized) return false;

  const history = getHistory(storage);
  const existing = history.find(item => item.url === normalized);
  const previousVisits = Number(existing?.visits || 0);
  const entry = {
    url: normalized,
    title: safeTitle(title, normalized),
    visitedAt,
    visits: (Number.isFinite(previousVisits) ? Math.max(0, previousVisits) : 0) + 1
  };

  const updated = [entry, ...history.filter(item => item.url !== normalized)]
    .slice(0, MAX_HISTORY);
  return writeCollection(STORAGE_KEYS.history, updated, storage);
}

export function updateHistoryTitle(url, title, storage) {
  const normalized = normalizeStoredUrl(url);
  if (!normalized || typeof title !== "string" || !title.trim()) return false;

  const history = getHistory(storage);
  const index = history.findIndex(item => item.url === normalized);
  if (index === -1) return false;

  history[index] = { ...history[index], title: safeTitle(title, normalized) };
  return writeCollection(STORAGE_KEYS.history, history, storage);
}

export function clearHistory(storage) {
  return writeCollection(STORAGE_KEYS.history, [], storage);
}

export function getDownloads(storage) {
  return readCollection(STORAGE_KEYS.downloads, storage)
    .filter(item => normalizeStoredUrl(item.url) !== null)
    .sort((left, right) => Number(right.completedAt || 0) - Number(left.completedAt || 0));
}

export function recordDownload(payload, storage, completedAt = Date.now()) {
  if (!payload || typeof payload !== "object") return false;

  const url = normalizeStoredUrl(payload.url);
  if (!url) return false;

  const entry = {
    id: String(completedAt) + ":" + url,
    url,
    path: typeof payload.path === "string" ? payload.path.slice(0, 4096) : "",
    success: payload.success === true,
    completedAt
  };

  const updated = [entry, ...getDownloads(storage)].slice(0, MAX_DOWNLOADS);
  return writeCollection(STORAGE_KEYS.downloads, updated, storage);
}

export function clearDownloads(storage) {
  return writeCollection(STORAGE_KEYS.downloads, [], storage);
}
