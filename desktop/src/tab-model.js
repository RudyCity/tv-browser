/* Pure tab ordering helpers. No DOM and no invoke, so the rules can be tested
   in isolation and shared with main.js once the view code is refactored. */

function tabList(tabs) {
  return Array.isArray(tabs) ? tabs : [];
}

function tabIdAt(tabs, index) {
  const tab = tabs[index];
  return tab && tab.id !== undefined && tab.id !== null ? tab.id : null;
}

export function indexOfTab(tabs, id) {
  if (id === null || id === undefined) return -1;
  return tabList(tabs).findIndex(tab => tab && tab.id === id);
}

/* Mirrors TabCollection::close in src-tauri/src/main.rs: remove the tab, then
   select the tab at min(removed index, remaining length - 1). */
export function neighbourIdOnClose(tabs, id) {
  const list = tabList(tabs);
  const index = indexOfTab(list, id);
  if (index < 0) return null;

  const remaining = list.slice(0, index).concat(list.slice(index + 1));
  if (!remaining.length) return null;

  return tabIdAt(remaining, Math.min(index, remaining.length - 1));
}

export function cycleTabId(tabs, activeId, direction) {
  const list = tabList(tabs);
  if (list.length < 2) return null;

  const step = direction === -1 ? -1 : 1;
  const index = indexOfTab(list, activeId);
  const nextIndex = index < 0
    ? (step > 0 ? 0 : list.length - 1)
    : (index + step + list.length) % list.length;

  return tabIdAt(list, nextIndex);
}

/* Mirrors TabCollection::move_to: pull the tab out, then insert it at the
   target index clamped to the remaining slots. */
export function moveTab(tabs, id, targetIndex) {
  if (!Array.isArray(tabs)) return [];
  const index = indexOfTab(tabs, id);
  if (index < 0) return tabs;

  const next = tabs.slice();
  const [tab] = next.splice(index, 1);
  const numeric = Number(targetIndex);
  const target = Number.isFinite(numeric) ? Math.trunc(numeric) : 0;
  const clamped = Math.min(next.length, Math.max(0, target));
  next.splice(clamped, 0, tab);

  return next;
}

/* A missing limit means "keep everything"; a zero or negative limit drops the
   entry, which is how a caller can disable closed tab recall. */
export function pushClosedTab(stack, tab, limit) {
  const entries = tabList(stack);
  if (tab === null || tab === undefined) return entries.slice();

  const numeric = Number(limit);
  const max = Number.isFinite(numeric) ? Math.max(0, Math.trunc(numeric)) : entries.length + 1;
  return [tab].concat(entries).slice(0, max);
}

export function takeClosedTab(stack) {
  const entries = tabList(stack);
  if (!entries.length) return { tab: null, rest: [] };
  return { tab: entries[0] ?? null, rest: entries.slice(1) };
}

/* Session payload shape consumed by saveSession in browser-data.js. Private
   tabs never reach disk, and zoom stays numeric so that module can clamp it. */
export function sessionTabs(tabs) {
  return tabList(tabs)
    .filter(tab => tab && typeof tab === "object" && !Array.isArray(tab) && tab.private !== true)
    .map(tab => {
      const zoom = Number(tab.zoom);
      return {
        url: typeof tab.url === "string" ? tab.url : "",
        title: typeof tab.title === "string" ? tab.title : "",
        zoom: Number.isFinite(zoom) && zoom > 0 ? zoom : 1
      };
    });
}
