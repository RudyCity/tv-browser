import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { normalizeDestination, normalizeNavigationState } from "./navigation.js";
import {
  addBookmark,
  clearDownloads,
  clearHistory,
  getBookmarks,
  getDownloads,
  getHistory,
  isBookmarked,
  recordDownload,
  recordVisit,
  removeBookmark,
  updateHistoryTitle
} from "./browser-data.js";

const address = document.querySelector("#address");
const homeAddress = document.querySelector("#home-address");
const welcome = document.querySelector("#welcome");
const homeContent = document.querySelector("#home-content");
const libraryView = document.querySelector("#library-view");
const libraryTitle = document.querySelector("#library-title");
const libraryFilter = document.querySelector("#library-filter");
const libraryItems = document.querySelector("#library-items");
const libraryEmpty = document.querySelector("#library-empty");
const libraryClear = document.querySelector("#library-clear");
const status = document.querySelector("#status");
const backButton = document.querySelector("#back");
const forwardButton = document.querySelector("#forward");
const bookmarkButton = document.querySelector("#bookmark-toggle");

let currentPageUrl = "";
let currentPageTitle = "";
let activeLibrary = null;

async function runCommand(name, args) {
  try {
    return await invoke(name, args);
  } catch (error) {
    status.textContent = String(error);
    return undefined;
  }
}

function setStatus(message) {
  status.textContent = String(message);
}

function applyNavigationState(state) {
  const availability = normalizeNavigationState(state);
  backButton.disabled = !availability.canGoBack;
  forwardButton.disabled = !availability.canGoForward;
}

async function refreshNavigationState() {
  if (!welcome.hidden) {
    applyNavigationState(null);
    return;
  }

  const state = await runCommand("browser_navigation_state");
  if (state !== undefined) applyNavigationState(state);
}

function fallbackTitle(url) {
  try {
    return new URL(url).hostname;
  } catch {
    return url;
  }
}

function syncBookmarkButton() {
  const saved = currentPageUrl ? isBookmarked(currentPageUrl) : false;
  bookmarkButton.disabled = !currentPageUrl;
  bookmarkButton.textContent = saved ? "★" : "☆";
  bookmarkButton.setAttribute("aria-pressed", String(saved));
  bookmarkButton.title = saved ? "Remove bookmark (Ctrl+D)" : "Bookmark this page (Ctrl+D)";
  bookmarkButton.setAttribute("aria-label", bookmarkButton.title);
}

function formatDate(value) {
  const timestamp = Number(value);
  if (!Number.isFinite(timestamp) || timestamp <= 0) return "";
  try {
    return new Date(timestamp).toLocaleString();
  } catch {
    return "";
  }
}

function makeLibraryItem(entry, options = {}) {
  const row = document.createElement("article");
  row.className = "library-item";

  const primary = document.createElement(options.clickable === false ? "div" : "button");
  if (primary instanceof HTMLButtonElement) primary.type = "button";
  primary.className = "library-item-main";

  const title = document.createElement("span");
  title.className = "library-item-title";
  title.textContent = options.title || entry.title || fallbackTitle(entry.url || "");

  const subtitle = document.createElement("span");
  subtitle.className = "library-item-subtitle";
  subtitle.textContent = options.subtitle || entry.url || "";

  primary.append(title, subtitle);
  if (typeof options.onOpen === "function" && primary instanceof HTMLButtonElement) {
    primary.addEventListener("click", options.onOpen);
  }
  row.append(primary);

  if (options.meta) {
    const meta = document.createElement("span");
    meta.className = "library-item-meta";
    meta.textContent = options.meta;
    row.append(meta);
  }

  if (typeof options.onRemove === "function") {
    const remove = document.createElement("button");
    remove.type = "button";
    remove.className = "remove-button";
    remove.textContent = "×";
    remove.title = "Remove";
    remove.setAttribute("aria-label", "Remove " + (entry.title || entry.url || "item"));
    remove.addEventListener("click", options.onRemove);
    row.append(remove);
  }

  return row;
}

function renderRows(container, entries, kind, limit) {
  container.replaceChildren();
  const visible = typeof limit === "number" ? entries.slice(0, limit) : entries;

  if (!visible.length) {
    const empty = document.createElement("p");
    empty.className = "empty-state";
    empty.textContent = kind === "bookmarks"
      ? "Pages you save will appear here."
      : kind === "downloads"
        ? "Completed downloads will appear here."
        : "Pages you visit will appear here.";
    container.append(empty);
    return;
  }

  for (const entry of visible) {
    if (kind === "downloads") {
      const filename = entry.path ? entry.path.split(/[\\/]/).pop() : fallbackTitle(entry.url);
      const state = entry.success ? "Downloaded" : "Download failed";
      container.append(makeLibraryItem(entry, {
        title: filename || fallbackTitle(entry.url),
        subtitle: entry.path || entry.url,
        meta: state,
        clickable: false
      }));
      continue;
    }

    const meta = kind === "history"
      ? (Number(entry.visits || 1) > 1 ? String(entry.visits) + " visits" : formatDate(entry.visitedAt))
      : "";
    container.append(makeLibraryItem(entry, {
      meta,
      onOpen: () => void navigate(entry.url),
      onRemove: kind === "bookmarks" ? () => {
        removeBookmark(entry.url);
        renderDashboard();
        renderLibrary();
        syncBookmarkButton();
      } : undefined
    }));
  }
}

function renderDashboard() {
  renderRows(document.querySelector("#home-bookmarks"), getBookmarks(), "bookmarks", 5);
  renderRows(document.querySelector("#home-recent"), getHistory(), "history", 5);
}

function renderLibrary() {
  if (!activeLibrary) return;

  const filter = libraryFilter.value.trim().toLowerCase();
  const collections = {
    bookmarks: getBookmarks(),
    history: getHistory(),
    downloads: getDownloads()
  };
  const entries = collections[activeLibrary].filter(entry => {
    const haystack = [entry.title, entry.url, entry.path].filter(Boolean).join(" ").toLowerCase();
    return !filter || haystack.includes(filter);
  });

  libraryItems.replaceChildren();
  libraryEmpty.hidden = entries.length > 0;
  libraryEmpty.textContent = filter ? "No matching items." : "Nothing here yet.";

  renderRows(libraryItems, entries, activeLibrary);
  if (entries.length === 0) libraryItems.replaceChildren();
}

function showHome() {
  activeLibrary = null;
  currentPageUrl = "";
  currentPageTitle = "";
  welcome.hidden = false;
  homeContent.hidden = false;
  libraryView.hidden = true;
  libraryFilter.value = "";
  renderDashboard();
  syncBookmarkButton();
  applyNavigationState(null);
  setStatus("Home");
}

async function openLibrary(kind) {
  await runCommand("browser_home");
  activeLibrary = kind;
  currentPageUrl = "";
  currentPageTitle = "";
  welcome.hidden = false;
  homeContent.hidden = true;
  libraryView.hidden = false;
  libraryFilter.value = "";
  syncBookmarkButton();

  const titles = {
    bookmarks: "Bookmarks",
    history: "History",
    downloads: "Downloads"
  };
  libraryTitle.textContent = titles[kind];
  libraryFilter.placeholder = kind === "downloads"
    ? "Filter by file name or address"
    : "Filter by title or address";
  libraryClear.hidden = kind === "bookmarks";
  libraryClear.textContent = kind === "history" ? "Clear history" : "Clear downloads";
  renderLibrary();
  applyNavigationState(null);
  setStatus(titles[kind]);
}

async function navigate(raw) {
  const target = normalizeDestination(raw);
  if (!target) {
    setStatus("Enter a valid address or search term");
    return;
  }

  activeLibrary = null;
  homeContent.hidden = false;
  libraryView.hidden = true;
  address.value = target;
  homeAddress.value = target;
  welcome.hidden = true;
  currentPageUrl = target;
  currentPageTitle = "";
  syncBookmarkButton();

  const result = await runCommand("browser_navigate", { url: target });
  if (result !== undefined) setStatus("Loading");
}

function toggleBookmark() {
  if (!currentPageUrl) {
    setStatus("Open a page before saving a bookmark");
    return;
  }

  if (isBookmarked(currentPageUrl)) {
    removeBookmark(currentPageUrl);
    setStatus("Bookmark removed");
  } else {
    addBookmark(currentPageUrl, currentPageTitle || fallbackTitle(currentPageUrl));
    setStatus("Bookmark saved");
  }

  syncBookmarkButton();
  renderDashboard();
  renderLibrary();
}

document.querySelector("#navigate").addEventListener("submit", event => {
  event.preventDefault();
  void navigate(address.value);
});

document.querySelector("#home-search").addEventListener("submit", event => {
  event.preventDefault();
  void navigate(homeAddress.value);
});

backButton.addEventListener("click", async () => {
  await runCommand("browser_back");
  await refreshNavigationState();
});
forwardButton.addEventListener("click", async () => {
  await runCommand("browser_forward");
  await refreshNavigationState();
});
document.querySelector("#reload").addEventListener("click", () => void runCommand("browser_reload"));
document.querySelector("#home").addEventListener("click", async () => {
  await runCommand("browser_home");
  showHome();
});
bookmarkButton.addEventListener("click", toggleBookmark);
document.querySelector("#bookmarks").addEventListener("click", () => void openLibrary("bookmarks"));
document.querySelector("#history").addEventListener("click", () => void openLibrary("history"));
document.querySelector("#downloads").addEventListener("click", () => void openLibrary("downloads"));
document.querySelector("#show-bookmarks").addEventListener("click", () => void openLibrary("bookmarks"));
document.querySelector("#show-history").addEventListener("click", () => void openLibrary("history"));
document.querySelector("#library-close").addEventListener("click", showHome);
libraryFilter.addEventListener("input", renderLibrary);

libraryClear.addEventListener("click", () => {
  if (activeLibrary === "history") {
    clearHistory();
    setStatus("History cleared");
  } else if (activeLibrary === "downloads") {
    clearDownloads();
    setStatus("Download history cleared");
  }
  renderDashboard();
  renderLibrary();
});

listen("browser-load", event => {
  const payload = event.payload;
  if (payload.event === "started") {
    welcome.hidden = true;
    currentPageUrl = payload.url;
    currentPageTitle = "";
    status.textContent = "Loading";
    address.value = payload.url;
    homeAddress.value = payload.url;
    syncBookmarkButton();
    return;
  }

  currentPageUrl = payload.url;
  recordVisit(payload.url, currentPageTitle || fallbackTitle(payload.url));
  status.textContent = "Ready";
  address.value = payload.url;
  homeAddress.value = payload.url;
  syncBookmarkButton();
  renderDashboard();
  renderLibrary();
  void refreshNavigationState();
}).catch(error => {
  status.textContent = "Browser event listener unavailable: " + String(error);
});

listen("browser-navigation-state", event => {
  applyNavigationState(event.payload);
}).catch(error => console.error("Navigation state listener unavailable", error));

listen("browser-navigation-state-error", event => {
  status.textContent = "Navigation state unavailable: " + String(event.payload || "unknown error");
}).catch(error => console.error("Navigation state error listener unavailable", error));

listen("browser-title", event => {
  currentPageTitle = String(event.payload || "").trim();
  document.title = currentPageTitle ? currentPageTitle + " — Rudy Browser" : "Rudy Browser";
  if (currentPageUrl) updateHistoryTitle(currentPageUrl, currentPageTitle);
  renderDashboard();
  renderLibrary();
}).catch(error => console.error("Title listener unavailable", error));

listen("browser-download-finished", event => {
  const payload = event.payload || {};
  recordDownload(payload);
  status.textContent = payload.success
    ? "Downloaded: " + (payload.path || "file saved")
    : "Download failed";
  renderLibrary();
}).catch(error => console.error("Download listener unavailable", error));

listen("browser-popup", event => {
  const payload = event.payload || {};
  status.textContent = payload.opened
    ? "Popup opened in current tab (single-view mode)"
    : "Popup blocked: " + (payload.reason || payload.error || "unsupported destination");
}).catch(error => console.error("Popup listener unavailable", error));

listen("browser-layout-error", event => {
  status.textContent = "Browser layout error: " + String(event.payload || "unknown error");
}).catch(error => console.error("Layout listener unavailable", error));

void runCommand("browser_layout");
void refreshNavigationState();
renderDashboard();
syncBookmarkButton();

// Same-document History API changes do not always trigger page-load events.
// Poll the current URL so Rust can synchronize its URL-based back/forward stack.
window.setInterval(() => {
  if (welcome.hidden) void refreshNavigationState();
}, 1000);

let layoutFrame = 0;
window.addEventListener("resize", () => {
  if (layoutFrame) return;
  layoutFrame = requestAnimationFrame(() => {
    layoutFrame = 0;
    void runCommand("browser_layout");
  });
});

document.addEventListener("keydown", event => {
  const command = event.ctrlKey || event.metaKey;
  const key = event.key.toLowerCase();

  if (command && key === "l") {
    event.preventDefault();
    address.focus();
    address.select();
  } else if (command && key === "d") {
    event.preventDefault();
    toggleBookmark();
  } else if (command && key === "b") {
    event.preventDefault();
    void openLibrary("bookmarks");
  } else if (command && key === "h") {
    event.preventDefault();
    void openLibrary("history");
  } else if (command && key === "j") {
    event.preventDefault();
    void openLibrary("downloads");
  } else if (command && key === "r") {
    event.preventDefault();
    void runCommand("browser_reload");
  } else if (event.altKey && event.key === "ArrowLeft" && !backButton.disabled) {
    event.preventDefault();
    void runCommand("browser_back");
  } else if (event.altKey && event.key === "ArrowRight" && !forwardButton.disabled) {
    event.preventDefault();
    void runCommand("browser_forward");
  } else if (event.key === "Escape" && activeLibrary) {
    showHome();
  }
});
