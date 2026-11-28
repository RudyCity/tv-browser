import { clampZoom, normalizeSearchEngine } from "./navigation.js";
import { normalizeLanguage } from "./strings.js";

const STORAGE_KEYS = Object.freeze({
  bookmarks: "rudy.browser.desktop.bookmarks.v1",
  history: "rudy.browser.desktop.history.v1",
  downloads: "rudy.browser.desktop.downloads.v1",
  settings: "rudy.browser.desktop.settings.v1",
  session: "rudy.browser.desktop.session.v1"
});

const MAX_BOOKMARKS = 300;
const MAX_HISTORY = 500;
const MAX_DOWNLOADS = 150;
const MAX_SESSION_TABS = 20;
const THEMES = ["dark", "light"];

export const DEFAULT_SETTINGS = Object.freeze({
  theme: "dark",
  language: "en",
  searchEngine: "google",
  startupUrl: "",
  defaultZoom: 1,
  permissions: Object.freeze({})
});

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

function normalizePermissions(value) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return {};

  const normalized = {};
  for (const [origin, kinds] of Object.entries(value)) {
    let parsedOrigin;
    try {
      const url = new URL(origin);
      if (!["http:", "https:"].includes(url.protocol)) continue;
      parsedOrigin = url.origin;
    } catch {
      continue;
    }

    if (!Array.isArray(kinds)) continue;
    const allowed = kinds
      .filter(kind => typeof kind === "string" && /^[a-z-]{2,24}$/.test(kind))
      .slice(0, 8);
    if (allowed.length) normalized[parsedOrigin] = [...new Set(allowed)];
  }

  return normalized;
}

function normalizeStartupUrl(value) {
  return typeof value === "string" ? normalizeStoredUrl(value) ?? "" : "";
}

export function normalizeSettings(value) {
  const source = value && typeof value === "object" && !Array.isArray(value) ? value : {};
  const theme = THEMES.includes(source.theme) ? source.theme : DEFAULT_SETTINGS.theme;

  return {
    theme,
    language: normalizeLanguage(source.language),
    searchEngine: normalizeSearchEngine(source.searchEngine),
    startupUrl: normalizeStartupUrl(source.startupUrl),
    defaultZoom: clampZoom(source.defaultZoom ?? DEFAULT_SETTINGS.defaultZoom),
    permissions: normalizePermissions(source.permissions)
  };
}

export function getSettings(storage) {
  const target = getStorage(storage);
  if (!target) return normalizeSettings(null);

  try {
    return normalizeSettings(JSON.parse(target.getItem(STORAGE_KEYS.settings) ?? "null"));
  } catch {
    return normalizeSettings(null);
  }
}

export function updateSettings(patch, storage) {
  const current = getSettings(storage);
  const next = normalizeSettings({ ...current, ...(patch && typeof patch === "object" ? patch : {}) });
  return writeCollection(STORAGE_KEYS.settings, next, storage) ? next : current;
}

function normalizeSessionTab(tab) {
  if (!tab || typeof tab !== "object" || Array.isArray(tab)) return null;

  const url = normalizeStoredUrl(tab.url);
  if (!url) return null;

  return {
    url,
    title: typeof tab.title === "string" ? tab.title.trim().slice(0, 300) : "",
    zoom: clampZoom(tab.zoom ?? 1)
  };
}

export function normalizeSession(value) {
  const source = value && typeof value === "object" && !Array.isArray(value) ? value : {};
  const tabs = Array.isArray(source.tabs)
    ? source.tabs
        .map(normalizeSessionTab)
        .filter(tab => tab !== null)
        .slice(0, MAX_SESSION_TABS)
    : [];

  const rawIndex = Number(source.activeIndex);
  const activeIndex = tabs.length && Number.isInteger(rawIndex)
    ? Math.min(tabs.length - 1, Math.max(0, rawIndex))
    : 0;

  return { tabs, activeIndex };
}

export function getSession(storage) {
  const target = getStorage(storage);
  if (!target) return { tabs: [], activeIndex: 0 };

  try {
    return normalizeSession(JSON.parse(target.getItem(STORAGE_KEYS.session) ?? "null"));
  } catch {
    return { tabs: [], activeIndex: 0 };
  }
}

export function saveSession(tabs, activeIndex, storage) {
  const restorable = Array.isArray(tabs)
    ? tabs
        .filter(tab => tab && tab.private !== true)
        .map(normalizeSessionTab)
        .filter(tab => tab !== null)
        .slice(0, MAX_SESSION_TABS)
    : [];

  return writeCollection(
    STORAGE_KEYS.session,
    { tabs: restorable, activeIndex: Number(activeIndex) || 0 },
    storage
  );
}

export function clearSession(storage) {
  return writeCollection(STORAGE_KEYS.session, { tabs: [], activeIndex: 0 }, storage);
}
