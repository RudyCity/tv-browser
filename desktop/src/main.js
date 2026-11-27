import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import {
  ZOOM_STEPS,
  clampZoom,
  describeSecurity,
  formatZoom,
  normalizeChromeHeight,
  normalizeDestination,
  normalizeNavigationState,
  stepZoom
} from "./navigation.js";
import {
  addBookmark,
  clearDownloads,
  clearHistory,
  getBookmarks,
  getDownloads,
  getHistory,
  getSession,
  getSettings,
  isBookmarked,
  normalizeStoredUrl,
  recordDownload,
  recordVisit,
  removeBookmark,
  saveSession,
  updateHistoryTitle,
  updateSettings
} from "./browser-data.js";

const $ = selector => document.querySelector(selector);

const chrome = $("#chrome");
const welcome = $("#welcome");
const homeContent = $("#home-content");
const libraryView = $("#library-view");
const settingsView = $("#settings-view");
const errorView = $("#error-view");
const tabList = $("#tab-list");
const address = $("#address");
const homeAddress = $("#home-address");
const status = $("#status");
const backButton = $("#back");
const forwardButton = $("#forward");
const bookmarkButton = $("#bookmark-toggle");
const securityChip = $("#security");
const zoomLevel = $("#zoom-level");
const progress = $("#progress");
const findBar = $("#find-bar");
const findTerm = $("#find-term");
const findCount = $("#find-count");
const libraryTitle = $("#library-title");
const libraryFilter = $("#library-filter");
const libraryItems = $("#library-items");
const libraryEmpty = $("#library-empty");
const libraryClear = $("#library-clear");
const permissionList = $("#permission-list");
const startupUrlInput = $("#settings-startup-url");
const startupEngineSelect = $("#settings-engine");
const defaultZoomSelect = $("#settings-default-zoom");

const PERMISSION_LABELS = Object.freeze({
  camera: "Camera",
  microphone: "Microphone",
  geolocation: "Location",
  notifications: "Notifications",
  "clipboard-read": "Clipboard read",
  "display-capture": "Screen capture"
});

const state = {
  settings: getSettings(),
  tabs: [],
  activeTabId: null,
  pageUrl: "",
  pageTitle: "",
  view: "home",
  libraryKind: null,
  zoom: 1,
  findOpen: false,
  chromeHeight: 0,
  sessionTimer: 0,
  layoutFrame: 0
};

function setStatus(message) {
  status.textContent = String(message);
}

async function runCommand(name, args) {
  try {
    return await invoke(name, args);
  } catch (error) {
    setStatus(String(error));
    return undefined;
  }
}

async function runVoid(name, args) {
  try {
    await invoke(name, args);
    return true;
  } catch (error) {
    setStatus(String(error));
    return false;
  }
}

function activeTab() {
  return state.tabs.find(tab => tab.id === state.activeTabId) ?? null;
}

/* Theme and settings -------------------------------------------------- */

function applyTheme() {
  document.documentElement.dataset.theme = state.settings.theme;
  const meta = document.querySelector('meta[name="theme-color"]');
  if (meta) meta.content = state.settings.theme === "light" ? "#F2F5FA" : "#0C131E";
}

function populateSettingsControls() {
  for (const radio of document.querySelectorAll('input[name="theme"]')) {
    radio.checked = radio.value === state.settings.theme;
  }
  startupEngineSelect.value = state.settings.searchEngine;
  startupUrlInput.value = state.settings.startupUrl;
  defaultZoomSelect.replaceChildren();
  for (const step of ZOOM_STEPS) {
    const option = document.createElement("option");
    option.value = String(step);
    option.textContent = formatZoom(step);
    defaultZoomSelect.append(option);
  }
  defaultZoomSelect.value = String(state.settings.defaultZoom);
}

function saveSettings(patch) {
  state.settings = updateSettings(patch);
  applyTheme();
  return state.settings;
}

function renderPermissionList() {
  const entries = Object.entries(state.settings.permissions).flatMap(([origin, kinds]) =>
    kinds.map(kind => ({ origin, kind }))
  );

  permissionList.replaceChildren();
  if (!entries.length) {
    const empty = document.createElement("p");
    empty.className = "empty-state";
    empty.textContent = "No site is allowed to request a permission.";
    permissionList.append(empty);
    return;
  }

  for (const entry of entries) {
    const row = document.createElement("div");
    row.className = "permission-item";

    const label = document.createElement("span");
    const origin = document.createElement("code");
    origin.textContent = entry.origin;
    label.append(origin, document.createTextNode(" " + (PERMISSION_LABELS[entry.kind] ?? entry.kind)));

    const remove = document.createElement("button");
    remove.type = "button";
    remove.className = "remove-button";
    remove.textContent = "×";
    remove.title = "Remove permission";
    remove.setAttribute("aria-label", "Remove " + (PERMISSION_LABELS[entry.kind] ?? entry.kind) + " for " + entry.origin);
    remove.addEventListener("click", () => {
      const nextPermissions = { ...state.settings.permissions };
      nextPermissions[entry.origin] = nextPermissions[entry.origin].filter(kind => kind !== entry.kind);
      if (!nextPermissions[entry.origin].length) delete nextPermissions[entry.origin];
      saveSettings({ permissions: nextPermissions });
      renderPermissionList();
      void syncPermissions();
      setStatus("Permission removed for " + entry.origin);
    });

    row.append(label, remove);
    permissionList.append(row);
  }
}

async function syncPermissions() {
  await runCommand("browser_permissions_set", { permissions: state.settings.permissions });
}

/* Tabs ---------------------------------------------------------------- */

function renderTabs() {
  tabList.replaceChildren();

  for (const tab of state.tabs) {
    const wrapper = document.createElement("div");
    wrapper.className = "tab" + (tab.private ? " tab-private" : "");
    wrapper.dataset.tabId = String(tab.id);
    wrapper.dataset.active = String(tab.id === state.activeTabId);

    const select = document.createElement("button");
    select.type = "button";
    select.className = "tab-select";
    select.setAttribute("role", "tab");
    select.setAttribute("aria-selected", String(tab.id === state.activeTabId));
    select.title = tab.url || (tab.private ? "Private tab" : "New tab");

    const title = document.createElement("span");
    title.className = "tab-title";
    title.textContent = tab.title || (tab.url ? fallbackTitle(tab.url) : tab.private ? "Private tab" : "New tab");
    select.append(title);
    select.addEventListener("click", () => void activateTab(tab.id));

    const close = document.createElement("button");
    close.type = "button";
    close.className = "tab-close";
    close.textContent = "×";
    close.title = "Close tab";
    close.setAttribute("aria-label", "Close " + title.textContent);
    close.addEventListener("click", event => {
      event.stopPropagation();
      void closeTab(tab.id);
    });

    wrapper.append(select, close);
    tabList.append(wrapper);
  }
}

function applyTabs(payload) {
  if (!payload || !Array.isArray(payload.tabs)) return;
  state.tabs = payload.tabs;
  state.activeTabId = payload.activeId ?? null;
  renderTabs();
  scheduleSessionSave();
}

function fallbackTitle(url) {
  try {
    return new URL(url).hostname;
  } catch {
    return url;
  }
}

async function createTab(url, isPrivate) {
  const tab = await runCommand("browser_tab_new", { url: url ?? null, incognito: isPrivate === true });
  if (!tab) return null;

  await activateTab(tab.id);
  if (state.settings.defaultZoom !== 1) await applyZoom(state.settings.defaultZoom);
  return tab;
}

async function activateTab(id) {
  const payload = await runCommand("browser_tab_activate", { id });
  if (!payload) return;

  applyTabs(payload);
  const tab = activeTab();
  state.pageUrl = tab?.url ?? "";
  state.pageTitle = tab?.title ?? "";

  if (state.pageUrl) {
    welcome.hidden = true;
    state.view = "page";
    address.value = state.pageUrl;
    updateSecurity(state.pageUrl);
  } else {
    showHome();
  }

  syncBookmarkButton();
  await refreshNavigationState();
  void refreshZoom();
}

async function closeTab(id) {
  const payload = await runCommand("browser_tab_close", { id });
  if (!payload) return;

  applyTabs(payload);
  const tab = activeTab();
  state.pageUrl = tab?.url ?? "";
  state.pageTitle = tab?.title ?? "";

  if (state.pageUrl) {
    welcome.hidden = true;
    state.view = "page";
    address.value = state.pageUrl;
    updateSecurity(state.pageUrl);
  } else {
    showHome();
  }
  syncBookmarkButton();
  await refreshNavigationState();
}

function scheduleSessionSave() {
  window.clearTimeout(state.sessionTimer);
  state.sessionTimer = window.setTimeout(() => {
    const index = state.tabs.findIndex(tab => tab.id === state.activeTabId);
    saveSession(state.tabs, index < 0 ? 0 : index);
  }, 350);
}

/* Views --------------------------------------------------------------- */

function hideInternalViews() {
  homeContent.hidden = true;
  libraryView.hidden = true;
  settingsView.hidden = true;
  errorView.hidden = true;
}

function showHome() {
  state.view = "home";
  state.libraryKind = null;
  welcome.hidden = false;
  hideInternalViews();
  homeContent.hidden = false;
  libraryFilter.value = "";
  renderDashboard();
  syncBookmarkButton();
  applyNavigationState(null);
  void runCommand("browser_home");
  setStatus("Home");
}

async function openLibrary(kind) {
  await runCommand("browser_home");
  state.view = "library";
  state.libraryKind = kind;
  welcome.hidden = false;
  hideInternalViews();
  libraryView.hidden = false;
  state.pageUrl = activeTab()?.url ?? "";
  syncBookmarkButton();

  const titles = { bookmarks: "Bookmarks", history: "History", downloads: "Downloads" };
  libraryTitle.textContent = titles[kind];
  libraryFilter.placeholder = kind === "downloads" ? "Filter by file name or address" : "Filter by title or address";
  libraryClear.hidden = kind === "bookmarks";
  libraryClear.textContent = kind === "history" ? "Clear history" : "Clear download activity";
  libraryFilter.value = "";
  renderLibrary();
  applyNavigationState(null);
  setStatus(titles[kind]);
}

function openSettings() {
  state.view = "settings";
  welcome.hidden = false;
  hideInternalViews();
  settingsView.hidden = false;
  state.pageUrl = activeTab()?.url ?? "";
  syncBookmarkButton();
  populateSettingsControls();
  renderPermissionList();
  void runCommand("browser_home");
  applyNavigationState(null);
  setStatus("Settings");
}

function showError(message, target) {
  state.view = "error";
  welcome.hidden = false;
  hideInternalViews();
  errorView.hidden = false;
  $("#error-message").textContent = message;
  $("#error-address").textContent = target || "";
  void runCommand("browser_home");
  setStatus("Address could not be opened");
}

/* Navigation ---------------------------------------------------------- */

function applyNavigationState(navState) {
  const availability = normalizeNavigationState(navState);
  backButton.disabled = !availability.canGoBack;
  forwardButton.disabled = !availability.canGoForward;
}

async function refreshNavigationState() {
  if (!state.pageUrl) {
    applyNavigationState(null);
    return;
  }

  const navState = await runCommand("browser_navigation_state");
  if (navState !== undefined) applyNavigationState(navState);
}

async function navigate(raw) {
  const target = normalizeDestination(raw, state.settings.searchEngine);
  if (!target) {
    showError("Enter an address or a search term that resolves to a web page.", String(raw ?? ""));
    return;
  }

  applyNavigationState(null);
  address.value = target;
  homeAddress.value = target;
  const accepted = await runVoid("browser_navigate", { url: target });
  if (!accepted) {
    showError("This address was rejected. Rudy Browser opens HTTP and HTTPS pages only.", target);
    return;
  }

  welcome.hidden = true;
  state.view = "page";
  state.pageUrl = target;
  state.pageTitle = "";
  updateSecurity(target);
  syncBookmarkButton();
  setStatus("Loading");
}

/* Page chrome indicators ---------------------------------------------- */

function updateSecurity(url) {
  const security = describeSecurity(url);
  securityChip.dataset.secure = String(security.secure);
  securityChip.title = security.host ? security.label + ": " + security.host : security.label;
  securityChip.setAttribute("aria-label", securityChip.title);
  securityChip.textContent = security.secure ? "◈" : "◇";
}

function setProgress(mode) {
  progress.dataset.state = mode === "loading" ? "loading" : mode === "failed" ? "failed" : "done";

  if (mode === "loading") return;

  window.setTimeout(() => {
    progress.dataset.state = "";
  }, mode === "failed" ? 1200 : 420);
}

function updateZoomLabel(zoom) {
  state.zoom = clampZoom(zoom);
  zoomLevel.textContent = formatZoom(state.zoom);
}

async function refreshZoom() {
  if (!state.pageUrl) {
    updateZoomLabel(1);
    return;
  }
  const zoom = await runCommand("browser_get_zoom");
  updateZoomLabel(typeof zoom === "number" ? zoom : 1);
}

async function applyZoom(next) {
  const zoom = clampZoom(next);
  const applied = await runCommand("browser_set_zoom", { zoom });
  updateZoomLabel(typeof applied === "number" ? applied : zoom);
  setStatus("Zoom " + formatZoom(state.zoom));
}

/* Bookmarks and library ----------------------------------------------- */

function syncBookmarkButton() {
  const url = state.pageUrl || activeTab()?.url || "";
  const saved = url ? isBookmarked(url) : false;
  bookmarkButton.disabled = !url;
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

  if (options.action) {
    const action = document.createElement("button");
    action.type = "button";
    action.className = "item-action";
    action.textContent = options.action.label;
    action.title = options.action.label;
    action.addEventListener("click", options.action.run);
    row.append(action);
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
      const itemState = entry.success ? "Downloaded" : "Failed";
      container.append(makeLibraryItem(entry, {
        title: filename || fallbackTitle(entry.url),
        subtitle: entry.path || entry.url,
        meta: itemState,
        clickable: false,
        action: entry.success && entry.path
          ? {
              label: "Open",
              run: () => void openPath(entry.path)
            }
          : undefined
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
  renderRows($("#home-bookmarks"), getBookmarks(), "bookmarks", 5);
  renderRows($("#home-recent"), getHistory(), "history", 5);
}

function renderLibrary() {
  if (state.view !== "library" || !state.libraryKind) return;

  const filter = libraryFilter.value.trim().toLowerCase();
  const collections = {
    bookmarks: getBookmarks(),
    history: getHistory(),
    downloads: getDownloads()
  };
  const entries = collections[state.libraryKind].filter(entry => {
    const haystack = [entry.title, entry.url, entry.path].filter(Boolean).join(" ").toLowerCase();
    return !filter || haystack.includes(filter);
  });

  libraryItems.replaceChildren();
  libraryEmpty.hidden = entries.length > 0;
  libraryEmpty.textContent = filter ? "No matching items." : "Nothing here yet.";
  renderRows(libraryItems, entries, state.libraryKind);
  if (entries.length === 0) libraryItems.replaceChildren();
}

async function openPath(target) {
  if (!target) return;
  const opened = await runVoid("browser_open_path", { path: target });
  setStatus(opened ? "Opened " + target : "Path could not be opened");
}

function toggleBookmark() {
  const url = state.pageUrl || activeTab()?.url || "";
  if (!url) {
    setStatus("Open a page before saving a bookmark");
    return;
  }

  if (isBookmarked(url)) {
    removeBookmark(url);
    setStatus("Bookmark removed");
  } else {
    addBookmark(url, state.pageTitle || fallbackTitle(url));
    setStatus("Bookmark saved");
  }

  syncBookmarkButton();
  renderDashboard();
  renderLibrary();
}

/* Find in page -------------------------------------------------------- */

function openFind() {
  if (!state.pageUrl) {
    setStatus("Find in page needs an open page");
    return;
  }

  state.findOpen = true;
  findBar.hidden = false;
  $("#find-toggle").setAttribute("aria-pressed", "true");
  findTerm.focus();
  findTerm.select();
}

function closeFind() {
  state.findOpen = false;
  findBar.hidden = true;
  findCount.textContent = "";
  $("#find-toggle").setAttribute("aria-pressed", "false");
}

async function runFind(forward) {
  const term = findTerm.value.trim();
  if (!term) {
    findCount.textContent = "";
    return;
  }

  findCount.textContent = "Searching";
  const started = await runVoid("browser_find", { term, forward: forward !== false });
  if (!started) findCount.textContent = "Unavailable";
}

function applyFindResult(payload) {
  if (!payload || payload.tabId !== state.activeTabId) return;
  const count = Number(payload.count || 0);
  findCount.textContent = count === 0 ? "No matches" : count === 1 ? "1 match" : count + " matches";
}

/* Chrome measurement -------------------------------------------------- */

function reportChromeHeight() {
  const height = normalizeChromeHeight(chrome.getBoundingClientRect().height);
  if (height === state.chromeHeight) return;
  state.chromeHeight = height;
  void runCommand("browser_layout", { chromeHeight: height });
}

function scheduleChromeReport() {
  if (state.layoutFrame) return;
  state.layoutFrame = requestAnimationFrame(() => {
    state.layoutFrame = 0;
    reportChromeHeight();
  });
}

/* Event wiring -------------------------------------------------------- */

function bindEvents() {
  $("#navigate").addEventListener("submit", event => {
    event.preventDefault();
    void navigate(address.value);
  });

  $("#home-search").addEventListener("submit", event => {
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

  $("#reload").addEventListener("click", () => {
    void runCommand("browser_reload");
    setProgress("loading");
  });

  $("#home").addEventListener("click", () => {
    void runCommand("browser_home");
    showHome();
  });

  $("#new-tab").addEventListener("click", () => void createTab(null, false));
  $("#new-private-tab").addEventListener("click", () => void createTab(null, true));

  bookmarkButton.addEventListener("click", toggleBookmark);
  $("#bookmarks").addEventListener("click", () => void openLibrary("bookmarks"));
  $("#history").addEventListener("click", () => void openLibrary("history"));
  $("#downloads").addEventListener("click", () => void openLibrary("downloads"));
  $("#settings-toggle").addEventListener("click", openSettings);
  $("#show-bookmarks").addEventListener("click", () => void openLibrary("bookmarks"));
  $("#show-history").addEventListener("click", () => void openLibrary("history"));
  $("#library-close").addEventListener("click", showHome);
  $("#settings-close").addEventListener("click", showHome);
  $("#error-close").addEventListener("click", showHome);
  $("#error-home").addEventListener("click", showHome);
  $("#error-retry").addEventListener("click", () => void navigate($("#error-address").textContent));

  libraryFilter.addEventListener("input", renderLibrary);

  libraryClear.addEventListener("click", () => {
    if (state.libraryKind === "history") {
      if (!window.confirm("Clear all browsing history? This cannot be undone.")) return;
      clearHistory();
      setStatus("History cleared");
    } else if (state.libraryKind === "downloads") {
      if (!window.confirm("Clear the download activity list? This cannot be undone.")) return;
      clearDownloads();
      setStatus("Download activity cleared");
    }
    renderDashboard();
    renderLibrary();
  });

  securityChip.addEventListener("click", () => {
    if (!state.pageUrl) return;
    setStatus(describeSecurity(state.pageUrl).label);
  });

  zoomLevel.addEventListener("click", () => void applyZoom(1));
  $("#zoom-out").addEventListener("click", () => void applyZoom(stepZoom(state.zoom, "out")));
  $("#zoom-in").addEventListener("click", () => void applyZoom(stepZoom(state.zoom, "in")));

  $("#find-toggle").addEventListener("click", () => {
    if (state.findOpen) {
      closeFind();
      return;
    }
    openFind();
  });
  $("#find-close").addEventListener("click", closeFind);
  $("#find-next").addEventListener("click", () => void runFind(true));
  $("#find-prev").addEventListener("click", () => void runFind(false));
  findTerm.addEventListener("input", () => void runFind(true));
  findBar.addEventListener("submit", event => {
    event.preventDefault();
    void runFind(true);
  });

  for (const radio of document.querySelectorAll('input[name="theme"]')) {
    radio.addEventListener("change", () => {
      if (!radio.checked) return;
      saveSettings({ theme: radio.value });
      setStatus("Theme set to " + radio.value);
    });
  }

  startupEngineSelect.addEventListener("change", () => {
    saveSettings({ searchEngine: startupEngineSelect.value });
    setStatus("Search engine set to " + startupEngineSelect.options[startupEngineSelect.selectedIndex].text);
  });

  startupUrlInput.addEventListener("change", () => {
    const normalized = normalizeStoredUrl(startupUrlInput.value);
    saveSettings({ startupUrl: normalized ?? "" });
    startupUrlInput.value = state.settings.startupUrl;
    setStatus(state.settings.startupUrl ? "Home address saved" : "Home address cleared");
  });

  defaultZoomSelect.addEventListener("change", () => {
    saveSettings({ defaultZoom: clampZoom(defaultZoomSelect.value) });
    void applyZoom(state.settings.defaultZoom);
  });

  $("#permission-form").addEventListener("submit", event => {
    event.preventDefault();
    const originInput = $("#permission-origin");
    const kindSelect = $("#permission-kind");

    let origin;
    try {
      const parsed = new URL(originInput.value.trim());
      if (!["http:", "https:"].includes(parsed.protocol)) throw new Error("scheme");
      origin = parsed.origin;
    } catch {
      setStatus("Enter an http or https origin, for example https://example.com");
      return;
    }

    const nextPermissions = { ...state.settings.permissions };
    const kinds = new Set(nextPermissions[origin] ?? []);
    kinds.add(kindSelect.value);
    nextPermissions[origin] = [...kinds];

    saveSettings({ permissions: nextPermissions });
    originInput.value = "";
    renderPermissionList();
    void syncPermissions();
    setStatus("Allowed " + (PERMISSION_LABELS[kindSelect.value] ?? kindSelect.value) + " for " + origin);
  });

  $("#clear-site-data").addEventListener("click", async () => {
    if (!window.confirm("Clear cookies and site data for every open tab? You will be signed out of sites.")) return;
    const cleared = await runVoid("browser_clear_site_data");
    setStatus(cleared ? "Cookies and site data cleared" : "Site data could not be cleared");
  });

  $("#clear-history").addEventListener("click", () => {
    if (!window.confirm("Clear all browsing history? This cannot be undone.")) return;
    clearHistory();
    renderDashboard();
    renderLibrary();
    setStatus("History cleared");
  });

  $("#clear-downloads").addEventListener("click", () => {
    if (!window.confirm("Clear the download activity list? This cannot be undone.")) return;
    clearDownloads();
    renderDashboard();
    renderLibrary();
    setStatus("Download activity cleared");
  });

  document.addEventListener("keydown", handleShortcut);
  new ResizeObserver(scheduleChromeReport).observe(chrome);
  window.addEventListener("resize", () => {
    scheduleChromeReport();
    void runCommand("browser_layout", { chromeHeight: state.chromeHeight || 0 });
  });
}

function handleShortcut(event) {
  const command = event.ctrlKey || event.metaKey;
  const key = event.key.toLowerCase();
  const inFindField = document.activeElement === findTerm;

  if (command && key === "l") {
    event.preventDefault();
    address.focus();
    address.select();
    return;
  }

  if (command && event.shiftKey && key === "n") {
    event.preventDefault();
    void createTab(null, true);
    return;
  }

  if (command && key === "t") {
    event.preventDefault();
    void createTab(null, false);
    return;
  }

  if (command && key === "w") {
    event.preventDefault();
    if (state.activeTabId !== null) void closeTab(state.activeTabId);
    return;
  }

  if (command && key === "tab") {
    event.preventDefault();
    cycleTabs(event.shiftKey ? -1 : 1);
    return;
  }

  if (command && key === "d") {
    event.preventDefault();
    toggleBookmark();
    return;
  }

  if (command && key === "b") {
    event.preventDefault();
    void openLibrary("bookmarks");
    return;
  }

  if (command && key === "h") {
    event.preventDefault();
    void openLibrary("history");
    return;
  }

  if (command && key === "j") {
    event.preventDefault();
    void openLibrary("downloads");
    return;
  }

  if (command && key === ",") {
    event.preventDefault();
    openSettings();
    return;
  }

  if (command && key === "f") {
    event.preventDefault();
    openFind();
    return;
  }

  if (command && key === "r") {
    event.preventDefault();
    void runCommand("browser_reload");
    setProgress("loading");
    return;
  }

  if (command && (key === "=" || key === "+")) {
    event.preventDefault();
    void applyZoom(stepZoom(state.zoom, "in"));
    return;
  }

  if (command && key === "-") {
    event.preventDefault();
    void applyZoom(stepZoom(state.zoom, "out"));
    return;
  }

  if (command && key === "0") {
    event.preventDefault();
    void applyZoom(1);
    return;
  }

  if (event.altKey && event.key === "ArrowLeft") {
    event.preventDefault();
    void runCommand("browser_back").then(refreshNavigationState);
    return;
  }

  if (event.altKey && event.key === "ArrowRight") {
    event.preventDefault();
    void runCommand("browser_forward").then(refreshNavigationState);
    return;
  }

  if (event.key === "F11") {
    event.preventDefault();
    void runCommand("browser_toggle_fullscreen");
    return;
  }

  if (event.key === "Enter" && inFindField) {
    event.preventDefault();
    void runFind(!event.shiftKey);
    return;
  }

  if (event.key === "Escape") {
    if (state.findOpen) {
      closeFind();
      return;
    }
    if (state.view !== "home") showHome();
  }
}

function cycleTabs(direction) {
  if (state.tabs.length < 2) return;
  const index = state.tabs.findIndex(tab => tab.id === state.activeTabId);
  const next = (index + direction + state.tabs.length) % state.tabs.length;
  void activateTab(state.tabs[next].id);
}

/* Native events ------------------------------------------------------- */

function bindNativeEvents() {
  listen("browser-load", event => {
    const payload = event.payload || {};
    const url = String(payload.url || "");

    if (payload.event === "started") {
      setProgress("loading");
      if (payload.tabId === state.activeTabId) {
        state.pageUrl = url;
        address.value = url;
        homeAddress.value = url;
        updateSecurity(url);
        welcome.hidden = true;
        state.view = "page";
        syncBookmarkButton();
        setStatus("Loading");
      }
      return;
    }

    setProgress("finished");
    const tab = state.tabs.find(candidate => candidate.id === payload.tabId);
    if (tab) {
      tab.url = url;
      tab.title = tab.title || fallbackTitle(url);
      if (!tab.private) recordVisit(url, tab.title);
    }

    if (payload.tabId === state.activeTabId) {
      state.pageUrl = url;
      address.value = url;
      homeAddress.value = url;
      updateSecurity(url);
      syncBookmarkButton();
      setStatus("Ready");
      void refreshNavigationState();
      void refreshZoom();
    }

    renderTabs();
    renderDashboard();
    renderLibrary();
    if (tab && !tab.private) scheduleSessionSave();
  }).catch(error => {
    setStatus("Browser event listener unavailable: " + String(error));
  });

  listen("browser-tab-title", event => {
    const payload = event.payload || {};
    const tab = state.tabs.find(candidate => candidate.id === payload.tabId);
    if (!tab) return;

    tab.title = String(payload.title || "").trim();
    renderTabs();

    if (payload.tabId === state.activeTabId) {
      state.pageTitle = tab.title;
      document.title = tab.title ? tab.title + " · Rudy Browser" : "Rudy Browser";
      if (tab.url && !tab.private) updateHistoryTitle(tab.url, tab.title);
      renderDashboard();
      renderLibrary();
    }
  }).catch(error => console.error("Title listener unavailable", error));

  listen("browser-tabs-changed", event => applyTabs(event.payload))
    .catch(error => console.error("Tab listener unavailable", error));

  listen("browser-find-result", event => applyFindResult(event.payload))
    .catch(error => console.error("Find listener unavailable", error));

  listen("browser-navigation-state", event => applyNavigationState(event.payload))
    .catch(error => console.error("Navigation state listener unavailable", error));

  listen("browser-navigation-state-error", event => {
    setStatus("Navigation state unavailable: " + String(event.payload || "unknown error"));
  }).catch(error => console.error("Navigation state error listener unavailable", error));

  listen("browser-download-finished", event => {
    const payload = event.payload || {};
    const tab = state.tabs.find(candidate => candidate.id === payload.tabId);
    if (!tab || !tab.private) recordDownload(payload);
    setStatus(payload.success ? "Downloaded: " + (payload.path || "file saved") : "Download failed");
    renderLibrary();
  }).catch(error => console.error("Download listener unavailable", error));

  listen("browser-popup", event => {
    const payload = event.payload || {};
    setStatus(payload.opened
      ? "Popup opened in the current tab (single-view mode)"
      : "Popup blocked: " + (payload.reason || payload.error || "unsupported destination"));
  }).catch(error => console.error("Popup listener unavailable", error));

  listen("browser-layout-error", event => {
    setStatus("Browser layout error: " + String(event.payload || "unknown error"));
  }).catch(error => console.error("Layout listener unavailable", error));

  listen("browser-page-probe", event => {
    const payload = event.payload || {};
    if (payload.failed !== true) return;

    const url = String(payload.url || "");
    if (payload.tabId !== state.activeTabId) {
      setStatus("Another tab failed to load: " + (url || "unknown address"));
      return;
    }

    setProgress("failed");
    showError(payload.reason || "This page could not be loaded.", url || state.pageUrl);
  }).catch(error => console.error("Page probe listener unavailable", error));
}

/* Startup ------------------------------------------------------------- */

async function restoreSession() {
  const session = getSession();

  if (session.tabs.length) {
    for (const tab of session.tabs) {
      await runCommand("browser_tab_new", { url: tab.url, incognito: false });
    }

    const list = await runCommand("browser_tab_list");
    if (list && Array.isArray(list.tabs) && list.tabs.length) {
      state.tabs = list.tabs;
      const target = session.tabs[Math.min(session.activeIndex, list.tabs.length - 1)];
      const match = list.tabs.find(tab => tab.url === target.url) ?? list.tabs[0];
      state.activeTabId = match.id;
      renderTabs();
      await activateTab(match.id);
      if (match.url) welcome.hidden = true;
      return;
    }
  }

  await createTab(state.settings.startupUrl || null, false);
  showHome();
}

async function start() {
  applyTheme();
  populateSettingsControls();
  renderPermissionList();
  bindEvents();
  bindNativeEvents();
  await syncPermissions();
  reportChromeHeight();
  await restoreSession();
  renderDashboard();
  syncBookmarkButton();
}

void start();
