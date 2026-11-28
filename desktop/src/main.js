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
  cycleTabId,
  indexOfTab,
  moveTab as reorderTabModel,
  neighbourIdOnClose,
  pushClosedTab,
  sessionTabs,
  takeClosedTab
} from "./tab-model.js";
import { normalizeLanguage, translate } from "./strings.js";
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
const statusText = $("#status-text");
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
const permissionNotice = $("#permission-notice");
const permissionNoticeText = $("#permission-notice-text");
const startupUrlInput = $("#settings-startup-url");
const startupEngineSelect = $("#settings-engine");
const defaultZoomSelect = $("#settings-default-zoom");
const languageSelect = $("#settings-language");

const PERMISSION_KIND_KEYS = Object.freeze({
  camera: "settings.permissionCamera",
  microphone: "settings.permissionMicrophone",
  geolocation: "settings.permissionLocation",
  notifications: "settings.permissionNotifications",
  "clipboard-read": "settings.permissionClipboardRead",
  "display-capture": "settings.permissionScreenCapture"
});

const LIBRARY_TITLE_KEYS = Object.freeze({
  bookmarks: "library.titleBookmarks",
  history: "library.titleHistory",
  downloads: "library.titleDownloads"
});

const DOWNLOAD_STATUS_KEYS = Object.freeze({
  running: "download.running",
  paused: "download.paused",
  completed: "download.completed",
  failed: "download.failed",
  cancelled: "download.cancelled"
});

const DOWNLOAD_COMMANDS = Object.freeze({
  pause: "browser_download_pause",
  resume: "browser_download_resume",
  cancel: "browser_download_cancel"
});

const MAX_CLOSED_TABS = 10;

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
  findCountKey: null,
  findCountParams: null,
  chromeHeight: 0,
  sessionTimer: 0,
  layoutFrame: 0,
  closedTabs: [],
  dragTabId: null,
  downloads: [],
  permissionRequest: null,
  error: null
};

function t(key, params) {
  return translate(state.settings.language, key, params);
}

function setStatus(message) {
  statusText.textContent = String(message);
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

/* Copy ----------------------------------------------------------------- */

function applyLanguage() {
  const language = state.settings.language;
  document.documentElement.lang = language;

  for (const node of document.querySelectorAll("[data-i18n]")) {
    node.textContent = t(node.getAttribute("data-i18n"));
  }
  for (const node of document.querySelectorAll("[data-i18n-aria]")) {
    node.setAttribute("aria-label", t(node.getAttribute("data-i18n-aria")));
  }
  for (const node of document.querySelectorAll("[data-i18n-title]")) {
    node.title = t(node.getAttribute("data-i18n-title"));
  }
  for (const node of document.querySelectorAll("[data-i18n-placeholder]")) {
    node.placeholder = t(node.getAttribute("data-i18n-placeholder"));
  }

  updateSecurity(state.pageUrl);
  updateDocumentTitle();
  syncBookmarkButton();
  renderTabs();
  renderPermissionList();
  renderPermissionNotice();
  renderFindCount();
  if (state.view === "library") {
    applyLibraryChrome();
    renderLibrary();
  }
  if (state.view === "error") applyErrorView();
}

function updateDocumentTitle() {
  document.title = state.pageTitle
    ? t("app.documentTitle", { title: state.pageTitle })
    : t("app.name");
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
  languageSelect.value = state.settings.language;
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
  const previousLanguage = state.settings.language;
  state.settings = updateSettings(patch);
  applyTheme();
  if (state.settings.language !== previousLanguage) applyLanguage();
  return state.settings;
}

/* Permissions --------------------------------------------------------- */

function permissionKindLabel(kind) {
  const key = PERMISSION_KIND_KEYS[kind];
  return key ? t(key) : String(kind ?? "");
}

function renderPermissionList() {
  const entries = Object.entries(state.settings.permissions).flatMap(([origin, kinds]) =>
    kinds.map(kind => ({ origin, kind }))
  );

  permissionList.replaceChildren();
  if (!entries.length) {
    const empty = document.createElement("p");
    empty.className = "empty-state";
    empty.textContent = t("settings.noPermissions");
    permissionList.append(empty);
    return;
  }

  for (const entry of entries) {
    const row = document.createElement("div");
    row.className = "permission-item";

    const label = document.createElement("span");
    const origin = document.createElement("code");
    origin.textContent = entry.origin;
    label.append(origin, document.createTextNode(" " + permissionKindLabel(entry.kind)));

    const remove = document.createElement("button");
    remove.type = "button";
    remove.className = "remove-button";
    remove.textContent = "×";
    remove.title = t("settings.removePermission");
    remove.setAttribute("aria-label", t("settings.removePermissionAria", {
      kind: permissionKindLabel(entry.kind),
      origin: entry.origin
    }));
    remove.addEventListener("click", () => {
      const nextPermissions = { ...state.settings.permissions };
      nextPermissions[entry.origin] = nextPermissions[entry.origin].filter(kind => kind !== entry.kind);
      if (!nextPermissions[entry.origin].length) delete nextPermissions[entry.origin];
      saveSettings({ permissions: nextPermissions });
      renderPermissionList();
      void syncPermissions();
      setStatus(t("status.permissionRemoved", { origin: entry.origin }));
    });

    row.append(label, remove);
    permissionList.append(row);
  }
}

async function syncPermissions() {
  await runCommand("browser_permissions_set", { permissions: state.settings.permissions });
}

function grantPermission(origin, kind) {
  const nextPermissions = { ...state.settings.permissions };
  const kinds = new Set(nextPermissions[origin] ?? []);
  kinds.add(kind);
  nextPermissions[origin] = [...kinds];
  saveSettings({ permissions: nextPermissions });
  renderPermissionList();
  void syncPermissions();
  return permissionKindLabel(kind);
}

function showPermissionRequest(payload) {
  const origin = typeof payload?.origin === "string" ? payload.origin.trim() : "";
  const kind = typeof payload?.kind === "string" ? payload.kind.trim() : "";
  if (!origin || !kind) return;

  state.permissionRequest = { origin, kind };
  renderPermissionNotice();
  setStatus(t("permission.request", { origin, kind: permissionKindLabel(kind) }));
}

function renderPermissionNotice() {
  const request = state.permissionRequest;
  permissionNotice.hidden = !request;
  permissionNoticeText.textContent = request
    ? t("permission.request", { origin: request.origin, kind: permissionKindLabel(request.kind) })
    : "";
}

function allowPermissionRequest() {
  const request = state.permissionRequest;
  if (!request) return;

  let origin;
  try {
    const parsed = new URL(request.origin);
    if (!["http:", "https:"].includes(parsed.protocol)) throw new Error("scheme");
    origin = parsed.origin;
  } catch {
    state.permissionRequest = null;
    renderPermissionNotice();
    setStatus(t("error.invalidPermissionOrigin"));
    return;
  }

  const kindLabel = grantPermission(origin, request.kind);
  state.permissionRequest = null;
  renderPermissionNotice();
  setStatus(t("status.permissionAllowed", { kind: kindLabel, origin }));
}

function dismissPermissionRequest() {
  state.permissionRequest = null;
  renderPermissionNotice();
}

/* Tabs ---------------------------------------------------------------- */

function fallbackTitle(url) {
  try {
    return new URL(url).hostname;
  } catch {
    return url;
  }
}

function tabDisplayTitle(tab) {
  if (tab.title) return tab.title;
  if (tab.url) return fallbackTitle(tab.url);
  return tab.private ? t("tabs.private") : t("tabs.new");
}

function renderTabs() {
  tabList.replaceChildren();

  for (const tab of state.tabs) {
    const wrapper = document.createElement("div");
    wrapper.className = "tab" + (tab.private ? " tab-private" : "");
    wrapper.dataset.tabId = String(tab.id);
    wrapper.dataset.active = String(tab.id === state.activeTabId);

    const displayTitle = tabDisplayTitle(tab);
    const emptyTitle = tab.private ? t("tabs.private") : t("tabs.new");

    const select = document.createElement("button");
    select.type = "button";
    select.className = "tab-select";
    select.setAttribute("role", "tab");
    select.setAttribute("aria-selected", String(tab.id === state.activeTabId));
    select.title = tab.url || emptyTitle;

    const title = document.createElement("span");
    title.className = "tab-title";
    title.textContent = displayTitle;
    select.append(title);
    select.addEventListener("click", () => void activateTab(tab.id));

    const close = document.createElement("button");
    close.type = "button";
    close.className = "tab-close";
    close.textContent = "×";
    close.title = t("tabs.close");
    close.setAttribute("aria-label", t("tabs.closeAria", { title: displayTitle }));
    close.addEventListener("click", event => {
      event.stopPropagation();
      void closeTab(tab.id);
    });

    wrapper.draggable = true;
    wrapper.addEventListener("dragstart", event => {
      state.dragTabId = tab.id;
      wrapper.dataset.dragging = "true";
      event.dataTransfer?.setData("text/plain", String(tab.id));
    });
    wrapper.addEventListener("dragend", () => {
      delete wrapper.dataset.dragging;
      state.dragTabId = null;
    });
    wrapper.addEventListener("dragover", event => {
      if (state.dragTabId === null) return;
      event.preventDefault();
    });
    wrapper.addEventListener("drop", event => {
      event.preventDefault();
      const draggedId = state.dragTabId;
      if (draggedId === null || draggedId === tab.id) return;
      const targetIndex = indexOfTab(state.tabs, tab.id);
      if (targetIndex < 0) return;
      void reorderTab(draggedId, targetIndex);
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

function syncActiveTab() {
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

  updateDocumentTitle();
  syncBookmarkButton();
  void refreshNavigationState();
  void refreshZoom();
}

async function createTab(url, isPrivate, restore = null) {
  const tab = await runCommand("browser_tab_new", {
    url: url ?? null,
    incognito: isPrivate === true,
    title: restore?.title ?? null,
    zoom: restore?.zoom ?? null
  });
  if (!tab) return null;

  await activateTab(tab.id);
  if (!restore && state.settings.defaultZoom !== 1) await applyZoom(state.settings.defaultZoom);
  return tab;
}

function rememberClosedTab(tab) {
  if (!tab || !tab.url) return;
  state.closedTabs = pushClosedTab(state.closedTabs, {
    url: tab.url,
    title: tab.title ?? "",
    private: tab.private === true,
    zoom: tab.zoom ?? 1
  }, MAX_CLOSED_TABS);
}

async function reopenClosedTab() {
  const { tab, rest } = takeClosedTab(state.closedTabs);
  state.closedTabs = rest;
  if (!tab) {
    setStatus(t("status.noClosedTab"));
    return;
  }

  await createTab(tab.url, tab.private, { title: tab.title, zoom: tab.zoom });
  setStatus(tab.private
    ? t("status.reopenedPrivateTab")
    : t("status.reopenedTab", { title: tab.title || tab.url }));
}

async function reorderTab(id, targetIndex) {
  const reordered = reorderTabModel(state.tabs, id, targetIndex);
  const index = indexOfTab(reordered, id);
  if (index < 0) return;

  state.tabs = reordered;
  renderTabs();

  const payload = await runCommand("browser_tab_move", { id, index });
  if (payload) applyTabs(payload);
  setStatus(t("status.tabOrderUpdated"));
}

async function activateTab(id) {
  const payload = await runCommand("browser_tab_activate", { id });
  if (!payload) return;

  applyTabs(payload);
  syncActiveTab();
}

async function closeTab(id) {
  const closing = state.tabs.find(tab => tab.id === id);
  const neighbour = neighbourIdOnClose(state.tabs, id);

  const payload = await runCommand("browser_tab_close", { id });
  if (!payload) return;

  rememberClosedTab(closing);
  applyTabs(payload);

  if (neighbour !== null && state.activeTabId !== neighbour) {
    await activateTab(neighbour);
    return;
  }
  syncActiveTab();
}

function scheduleSessionSave() {
  window.clearTimeout(state.sessionTimer);
  state.sessionTimer = window.setTimeout(() => {
    const restorable = state.tabs.filter(tab => !tab.private);
    saveSession(sessionTabs(state.tabs), Math.max(0, indexOfTab(restorable, state.activeTabId)));
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
  setStatus(t("status.home"));
}

function applyLibraryChrome() {
  const kind = state.libraryKind;
  if (!kind) return;

  libraryTitle.textContent = t(LIBRARY_TITLE_KEYS[kind]);
  libraryFilter.placeholder = t(kind === "downloads" ? "library.filterPlaceholderDownloads" : "library.filterPlaceholder");
  libraryClear.hidden = kind === "bookmarks";
  libraryClear.textContent = t(kind === "history" ? "library.clearHistory" : "library.clearDownloads");
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
  applyLibraryChrome();
  libraryFilter.value = "";
  renderLibrary();
  applyNavigationState(null);
  setStatus(t(LIBRARY_TITLE_KEYS[kind]));
  if (kind === "downloads") void loadDownloads();
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
  setStatus(t("status.settings"));
}

function showError(messageKey, target, raw) {
  state.error = {
    key: messageKey || null,
    raw: raw ? String(raw) : "",
    target: target ? String(target) : ""
  };
  state.view = "error";
  welcome.hidden = false;
  hideInternalViews();
  errorView.hidden = false;
  applyErrorView();
  void runCommand("browser_home");
  setStatus(t("status.addressFailed"));
}

function applyErrorView() {
  const error = state.error;
  $("#error-message").textContent = error?.key ? t(error.key) : error?.raw || t("error.pageFailed");
  $("#error-address").textContent = error?.target || "";
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
    showError("error.emptyInput", String(raw ?? ""));
    return;
  }

  applyNavigationState(null);
  address.value = target;
  homeAddress.value = target;
  const accepted = await runVoid("browser_navigate", { url: target });
  if (!accepted) {
    showError("error.addressRejected", target);
    return;
  }

  welcome.hidden = true;
  state.view = "page";
  state.pageUrl = target;
  state.pageTitle = "";
  updateSecurity(target);
  updateDocumentTitle();
  syncBookmarkButton();
  setStatus(t("status.loading"));
}

/* Page chrome indicators ---------------------------------------------- */

function describeSecurityLabel(rawUrl) {
  if (typeof rawUrl !== "string" || !rawUrl.trim()) {
    return { secure: false, text: t("security.noPage") };
  }

  const security = describeSecurity(rawUrl);
  if (!security.host) return { secure: false, text: t("security.unknownAddress") };

  const stateKey = security.secure
    ? "security.secure"
    : rawUrl.trim().toLowerCase().startsWith("http:")
      ? "security.insecure"
      : "security.internal";

  return {
    secure: security.secure,
    text: t("security.withHost", { state: t(stateKey), host: security.host })
  };
}

function updateSecurity(url) {
  const security = describeSecurityLabel(url);
  securityChip.dataset.secure = String(security.secure);
  securityChip.title = security.text;
  securityChip.setAttribute("aria-label", security.text);
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
  setStatus(t("status.zoomChanged", { percent: formatZoom(state.zoom) }));
}

/* Bookmarks and library ----------------------------------------------- */

function syncBookmarkButton() {
  const url = state.pageUrl || activeTab()?.url || "";
  const saved = url ? isBookmarked(url) : false;
  bookmarkButton.disabled = !url;
  bookmarkButton.textContent = saved ? "★" : "☆";
  bookmarkButton.setAttribute("aria-pressed", String(saved));
  bookmarkButton.title = saved ? t("toolbar.removeBookmarkTitle") : t("toolbar.bookmarkPageTitle");
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
    remove.title = t("library.remove");
    remove.setAttribute("aria-label", t("library.removeAria", {
      title: entry.title || entry.url || t("library.remove")
    }));
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
      ? t("library.emptyBookmarks")
      : t("library.emptyHistory");
    container.append(empty);
    return;
  }

  for (const entry of visible) {
    const meta = kind === "history"
      ? (Number(entry.visits || 1) > 1
          ? t("library.visits", { count: Number(entry.visits) })
          : formatDate(entry.visitedAt))
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

function matchesLibraryFilter(entry, filter) {
  const haystack = [entry.title, entry.url, entry.path].filter(Boolean).join(" ").toLowerCase();
  return !filter || haystack.includes(filter);
}

function renderLibrary() {
  if (state.view !== "library" || !state.libraryKind) return;

  const kind = state.libraryKind;
  const filter = libraryFilter.value.trim().toLowerCase();
  const source = kind === "downloads"
    ? downloadEntries()
    : kind === "bookmarks" ? getBookmarks() : getHistory();
  const entries = source.filter(entry => matchesLibraryFilter(entry, filter));

  libraryItems.replaceChildren();
  libraryEmpty.hidden = entries.length > 0;
  libraryEmpty.textContent = filter ? t("library.noMatches") : t("library.empty");

  if (!entries.length) return;

  if (kind === "downloads") {
    for (const entry of entries) libraryItems.append(renderDownloadItem(entry));
    return;
  }

  renderRows(libraryItems, entries, kind);
}

async function openPath(target) {
  if (!target) return;
  const opened = await runVoid("browser_open_path", { path: target });
  setStatus(opened ? t("status.opened", { path: target }) : t("status.pathRejected"));
}

function toggleBookmark() {
  const url = state.pageUrl || activeTab()?.url || "";
  if (!url) {
    setStatus(t("status.openPageFirst"));
    return;
  }

  if (isBookmarked(url)) {
    removeBookmark(url);
    setStatus(t("status.bookmarkRemoved"));
  } else {
    addBookmark(url, state.pageTitle || fallbackTitle(url));
    setStatus(t("status.bookmarkSaved"));
  }

  syncBookmarkButton();
  renderDashboard();
  renderLibrary();
}

/* Downloads ----------------------------------------------------------- */

function downloadStatusOf(entry) {
  const status = typeof entry.status === "string" ? entry.status : "";
  if (Object.hasOwn(DOWNLOAD_STATUS_KEYS, status)) return status;
  return entry.success === true ? "completed" : "failed";
}

function downloadStatusLabel(status) {
  const key = DOWNLOAD_STATUS_KEYS[status];
  return key ? t(key) : String(status ?? "");
}

function downloadPercentOf(entry) {
  const total = Number(entry.total);
  if (!Number.isFinite(total) || total <= 0) return null;
  const percent = Number(entry.percent);
  if (!Number.isFinite(percent)) return 0;
  return Math.min(100, Math.max(0, percent));
}

function formatDownloadPercent(percent) {
  const value = Math.round(percent * 10) / 10;
  return Number.isInteger(value) ? String(value) : value.toFixed(1);
}

function activeDownloads() {
  return state.downloads.filter(entry => {
    const status = downloadStatusOf(entry);
    return status === "running" || status === "paused";
  });
}

function downloadEntries() {
  const live = state.downloads.slice().reverse();
  const seen = new Set(live.map(entry => String(entry.url || "") + "\n" + String(entry.path || "")));
  const recorded = getDownloads()
    .filter(entry => !seen.has(String(entry.url || "") + "\n" + String(entry.path || "")))
    .map(entry => ({ ...entry, live: false }));

  return live.map(entry => ({ ...entry, live: true })).concat(recorded);
}

function updateDownload(payload) {
  if (!payload || typeof payload !== "object") return;
  const id = Number(payload.id);
  if (!Number.isFinite(id)) return;

  const index = state.downloads.findIndex(entry => Number(entry.id) === id);
  if (index === -1) state.downloads.push({ ...payload });
  else state.downloads[index] = { ...state.downloads[index], ...payload };

  if (state.view === "library" && state.libraryKind === "downloads") renderLibrary();
}

async function loadDownloads() {
  const payload = await runCommand("browser_download_list");
  if (!payload || !Array.isArray(payload.downloads)) return;

  state.downloads = payload.downloads.filter(entry => entry && typeof entry === "object" && !Array.isArray(entry));
  if (state.view === "library" && state.libraryKind === "downloads") renderLibrary();
}

async function controlDownload(id, action) {
  const command = DOWNLOAD_COMMANDS[action];
  if (!command) return;

  const payload = await runCommand(command, { id: Number(id) });
  if (payload) updateDownload(payload);
}

function downloadActionButton(key, onClick, extraClass) {
  const button = document.createElement("button");
  button.type = "button";
  button.className = "item-action" + (extraClass ? " " + extraClass : "");
  button.textContent = t(key);
  button.addEventListener("click", onClick);
  return button;
}

function renderDownloadItem(entry) {
  const status = downloadStatusOf(entry);
  const percent = downloadPercentOf(entry);

  const row = document.createElement("article");
  row.className = "library-item download-item";
  row.dataset.status = status;
  if (entry.id !== undefined && entry.id !== null) row.dataset.downloadId = String(entry.id);

  const main = document.createElement("div");
  main.className = "library-item-main";

  const filename = entry.path ? String(entry.path).split(/[\\/]/).pop() : "";
  const title = document.createElement("span");
  title.className = "library-item-title";
  title.textContent = filename || fallbackTitle(String(entry.url || ""));

  const subtitle = document.createElement("span");
  subtitle.className = "library-item-subtitle";
  subtitle.textContent = String(entry.path || entry.url || "");

  const progressBar = document.createElement("div");
  progressBar.className = "download-progress";
  const indeterminate = percent === null && (status === "running" || status === "paused");
  progressBar.dataset.state = indeterminate ? "indeterminate" : "determinate";
  if (!indeterminate) {
    progressBar.setAttribute("role", "progressbar");
    progressBar.setAttribute("aria-valuemin", "0");
    progressBar.setAttribute("aria-valuemax", "100");
    progressBar.setAttribute("aria-valuenow", String(Math.round(percent ?? 0)));
  }

  const fill = document.createElement("div");
  fill.className = "download-progress-fill";
  if (!indeterminate) fill.style.width = String(percent ?? 0) + "%";
  progressBar.append(fill);

  main.append(title, subtitle, progressBar);

  if (status === "failed" && entry.error) {
    const reason = document.createElement("span");
    reason.className = "download-error";
    reason.textContent = String(entry.error);
    main.append(reason);
  }

  const meta = document.createElement("span");
  meta.className = "library-item-meta";
  meta.textContent = percent === null
    ? downloadStatusLabel(status)
    : downloadStatusLabel(status) + " " + t("download.progress", { percent: formatDownloadPercent(percent) });

  row.append(main, meta);

  const actions = document.createElement("div");
  actions.className = "download-actions";

  if (entry.live) {
    const id = Number(entry.id);
    if (status === "running") {
      actions.append(downloadActionButton("download.pause", () => void controlDownload(id, "pause")));
      actions.append(downloadActionButton("download.cancel", () => void controlDownload(id, "cancel"), "download-cancel"));
    } else if (status === "paused") {
      actions.append(downloadActionButton("download.resume", () => void controlDownload(id, "resume")));
      actions.append(downloadActionButton("download.cancel", () => void controlDownload(id, "cancel"), "download-cancel"));
    }
  }

  if (status === "completed" && entry.path) {
    actions.append(downloadActionButton("library.open", () => void openPath(String(entry.path))));
  }

  if (actions.childElementCount) row.append(actions);
  return row;
}

/* Find in page -------------------------------------------------------- */

function setFindCount(key, params) {
  state.findCountKey = key ?? null;
  state.findCountParams = params ?? null;
  findCount.textContent = key ? t(key, params) : "";
}

function renderFindCount() {
  findCount.textContent = state.findCountKey ? t(state.findCountKey, state.findCountParams) : "";
}

function openFind() {
  if (!state.pageUrl) {
    setStatus(t("status.findNeedsPage"));
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
  setFindCount(null);
  $("#find-toggle").setAttribute("aria-pressed", "false");
}

async function runFind(forward) {
  const term = findTerm.value.trim();
  if (!term) {
    setFindCount(null);
    return;
  }

  setFindCount("find.searching");
  const started = await runVoid("browser_find", { term, forward: forward !== false });
  if (!started) setFindCount("find.unavailable");
}

function applyFindResult(payload) {
  if (!payload || payload.tabId !== state.activeTabId) return;
  const count = Number(payload.count || 0);
  if (count === 0) setFindCount("find.noMatches");
  else if (count === 1) setFindCount("find.oneMatch");
  else setFindCount("find.matches", { count });
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
  tabList.addEventListener("keydown", handleTabStripKeydown);

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
      if (!window.confirm(t("dialog.clearHistory"))) return;
      clearHistory();
      setStatus(t("status.historyCleared"));
    } else if (state.libraryKind === "downloads") {
      if (!window.confirm(t("dialog.clearDownloads"))) return;
      clearDownloads();
      state.downloads = activeDownloads();
      setStatus(t("status.downloadsCleared"));
    }
    renderDashboard();
    renderLibrary();
  });

  securityChip.addEventListener("click", () => {
    if (!state.pageUrl) return;
    setStatus(describeSecurityLabel(state.pageUrl).text);
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
      setStatus(t("status.themeSet", {
        theme: t(radio.value === "light" ? "settings.themeLight" : "settings.themeDark")
      }));
    });
  }

  languageSelect.addEventListener("change", () => {
    saveSettings({ language: normalizeLanguage(languageSelect.value) });
    setStatus(t("status.ready"));
  });

  startupEngineSelect.addEventListener("change", () => {
    saveSettings({ searchEngine: startupEngineSelect.value });
    setStatus(t("status.searchEngineSet", {
      engine: startupEngineSelect.options[startupEngineSelect.selectedIndex].text
    }));
  });

  startupUrlInput.addEventListener("change", () => {
    const normalized = normalizeStoredUrl(startupUrlInput.value);
    saveSettings({ startupUrl: normalized ?? "" });
    startupUrlInput.value = state.settings.startupUrl;
    setStatus(state.settings.startupUrl ? t("status.homeAddressSaved") : t("status.homeAddressCleared"));
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
      setStatus(t("error.invalidPermissionOrigin"));
      return;
    }

    const kindLabel = grantPermission(origin, kindSelect.value);
    originInput.value = "";
    setStatus(t("status.permissionAllowed", { kind: kindLabel, origin }));
  });

  $("#permission-allow").addEventListener("click", allowPermissionRequest);
  $("#permission-dismiss").addEventListener("click", dismissPermissionRequest);

  $("#clear-site-data").addEventListener("click", async () => {
    if (!window.confirm(t("dialog.clearSiteData"))) return;
    const cleared = await runVoid("browser_clear_site_data");
    setStatus(cleared ? t("status.siteDataCleared") : t("status.siteDataFailed"));
  });

  $("#clear-history").addEventListener("click", () => {
    if (!window.confirm(t("dialog.clearHistory"))) return;
    clearHistory();
    renderDashboard();
    renderLibrary();
    setStatus(t("status.historyCleared"));
  });

  $("#clear-downloads").addEventListener("click", () => {
    if (!window.confirm(t("dialog.clearDownloads"))) return;
    clearDownloads();
    state.downloads = activeDownloads();
    renderDashboard();
    renderLibrary();
    setStatus(t("status.downloadsCleared"));
  });

  document.addEventListener("keydown", handleShortcut);
  new ResizeObserver(scheduleChromeReport).observe(chrome);
  window.addEventListener("resize", () => {
    scheduleChromeReport();
    void runCommand("browser_layout", { chromeHeight: state.chromeHeight || 0 });
  });
}

function handleTabStripKeydown(event) {
  const keys = ["ArrowLeft", "ArrowRight", "Home", "End"];
  if (!keys.includes(event.key)) return;

  const selectors = [...tabList.querySelectorAll(".tab-select")];
  if (!selectors.length) return;

  const current = selectors.indexOf(document.activeElement);
  let next;
  if (event.key === "Home") next = 0;
  else if (event.key === "End") next = selectors.length - 1;
  else {
    const delta = event.key === "ArrowLeft" ? -1 : 1;
    next = current < 0 ? 0 : (current + delta + selectors.length) % selectors.length;
  }

  event.preventDefault();
  selectors[next].focus();
  selectors[next].click();
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

  if (command && event.shiftKey && key === "t") {
    event.preventDefault();
    void reopenClosedTab();
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
    if (state.permissionRequest) {
      dismissPermissionRequest();
      return;
    }
    if (state.view !== "home") showHome();
  }
}

function cycleTabs(direction) {
  const nextId = cycleTabId(state.tabs, state.activeTabId, direction);
  if (nextId === null) return;
  void activateTab(nextId);
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
        setStatus(t("status.loading"));
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
      setStatus(t("status.ready"));
      void refreshNavigationState();
      void refreshZoom();
    }

    renderTabs();
    renderDashboard();
    renderLibrary();
    if (tab && !tab.private) scheduleSessionSave();
  }).catch(error => {
    setStatus(t("status.eventListenerUnavailable", { reason: String(error) }));
  });

  listen("browser-tab-title", event => {
    const payload = event.payload || {};
    const tab = state.tabs.find(candidate => candidate.id === payload.tabId);
    if (!tab) return;

    tab.title = String(payload.title || "").trim();
    renderTabs();

    if (payload.tabId === state.activeTabId) {
      state.pageTitle = tab.title;
      updateDocumentTitle();
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
    setStatus(t("status.navigationStateUnavailable", {
      reason: String(event.payload || t("status.unknownError"))
    }));
  }).catch(error => console.error("Navigation state error listener unavailable", error));

  listen("browser-download-progress", event => {
    updateDownload(event.payload);
  }).catch(error => console.error("Download progress listener unavailable", error));

  listen("browser-download-finished", event => {
    const payload = event.payload || {};
    const tab = state.tabs.find(candidate => candidate.id === payload.tabId);
    if (!tab || !tab.private) recordDownload(payload);
    setStatus(payload.success
      ? t("status.downloaded", { path: payload.path || t("status.fileSaved") })
      : t("status.downloadFailed"));
    renderLibrary();
  }).catch(error => console.error("Download listener unavailable", error));

  listen("browser-permission-request", event => {
    showPermissionRequest(event.payload);
  }).catch(error => console.error("Permission listener unavailable", error));

  listen("browser-popup", event => {
    const payload = event.payload || {};
    setStatus(payload.opened
      ? t("status.popupOpened")
      : t("status.popupBlocked", {
          reason: payload.reason || payload.error || t("status.unsupportedDestination")
        }));
  }).catch(error => console.error("Popup listener unavailable", error));

  listen("browser-layout-error", event => {
    setStatus(t("status.layoutError", {
      reason: String(event.payload || t("status.unknownError"))
    }));
  }).catch(error => console.error("Layout listener unavailable", error));

  listen("browser-page-probe", event => {
    const payload = event.payload || {};
    if (payload.failed !== true) return;

    const url = String(payload.url || "");
    if (payload.tabId !== state.activeTabId) {
      setStatus(t("status.otherTabFailed", { address: url || t("status.unknownAddress") }));
      return;
    }

    setProgress("failed");
    showError(null, url || state.pageUrl, payload.reason);
  }).catch(error => console.error("Page probe listener unavailable", error));
}

/* Startup ------------------------------------------------------------- */

async function restoreSession() {
  const session = getSession();

  if (session.tabs.length) {
    for (const tab of session.tabs) {
      await runCommand("browser_tab_new", {
        url: tab.url,
        incognito: false,
        title: tab.title,
        zoom: tab.zoom
      });
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
  applyLanguage();
  bindEvents();
  bindNativeEvents();
  setStatus(t("status.ready"));
  await syncPermissions();
  reportChromeHeight();
  await restoreSession();
  renderDashboard();
  syncBookmarkButton();
  updateDocumentTitle();
}

void start();
