export const SEARCH_ENGINES = Object.freeze({
  google: Object.freeze({ label: "Google", template: "https://www.google.com/search?q=" }),
  duckduckgo: Object.freeze({ label: "DuckDuckGo", template: "https://duckduckgo.com/?q=" }),
  bing: Object.freeze({ label: "Bing", template: "https://www.bing.com/search?q=" })
});

const DEFAULT_SEARCH_ENGINE = "google";

export const ZOOM_STEPS = Object.freeze([0.5, 0.67, 0.8, 0.9, 1, 1.1, 1.25, 1.5, 1.75, 2, 2.5, 3]);

export const MIN_ZOOM = ZOOM_STEPS[0];
export const MAX_ZOOM = ZOOM_STEPS[ZOOM_STEPS.length - 1];

export const DEFAULT_CHROME_HEIGHT = 116;

export function normalizeSearchEngine(value) {
  const key = String(value ?? "").trim().toLowerCase();
  return Object.hasOwn(SEARCH_ENGINES, key) ? key : DEFAULT_SEARCH_ENGINE;
}

export function buildSearchUrl(term, engine) {
  const query = String(term ?? "").trim();
  if (!query) return null;

  const key = normalizeSearchEngine(engine);
  return SEARCH_ENGINES[key].template + encodeURIComponent(query);
}

export function normalizeDestination(raw, engine) {
  if (typeof raw !== "string") return null;
  const value = raw.trim();
  if (!value) return null;

  // Keep local development servers usable without accepting arbitrary schemes.
  if (/^(localhost|127\.0\.0\.1)(:\d+)?([/].*)?$/i.test(value)) {
    try { return new URL(`http://${value}`).href; } catch { return null; }
  }

  if (/^[a-z][a-z0-9+.-]*:/i.test(value)) {
    try {
      const url = new URL(value);
      return ["http:", "https:"].includes(url.protocol) && url.hostname ? url.href : null;
    } catch { return null; }
  }

  if (/\s/.test(value) || !value.includes(".")) {
    return buildSearchUrl(value, engine);
  }

  try {
    const url = new URL(`https://${value}`);
    return url.hostname ? url.href : null;
  } catch { return null; }
}

export function normalizeNavigationState(state) {
  if (state === null || typeof state !== "object") {
    return { canGoBack: false, canGoForward: false };
  }

  return {
    canGoBack: state.canGoBack === true,
    canGoForward: state.canGoForward === true
  };
}

export function clampZoom(value) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric) || numeric <= 0) return 1;
  return Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, Math.round(numeric * 100) / 100));
}

export function stepZoom(current, direction) {
  const zoom = clampZoom(current);
  const index = ZOOM_STEPS.indexOf(zoom);
  const step = direction === "in" ? 1 : direction === "out" ? -1 : 0;

  if (step === 0) return 1;
  if (index === -1) {
    const next = step > 0
      ? ZOOM_STEPS.find(candidate => candidate > zoom)
      : [...ZOOM_STEPS].reverse().find(candidate => candidate < zoom);
    return next ?? zoom;
  }

  const target = Math.min(ZOOM_STEPS.length - 1, Math.max(0, index + step));
  return ZOOM_STEPS[target];
}

export function formatZoom(value) {
  return `${Math.round(clampZoom(value) * 100)}%`;
}

export function describeSecurity(rawUrl) {
  if (typeof rawUrl !== "string" || !rawUrl.trim()) {
    return { label: "No page", secure: false, host: "" };
  }

  let url;
  try {
    url = new URL(rawUrl);
  } catch {
    return { label: "Unknown address", secure: false, host: "" };
  }

  if (url.protocol === "https:") {
    return { label: "Connection is secure", secure: true, host: url.hostname };
  }

  if (url.protocol === "http:") {
    return { label: "Connection is not secure", secure: false, host: url.hostname };
  }

  return { label: "Internal page", secure: false, host: url.hostname };
}

export function normalizeChromeHeight(value) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric) || numeric <= 0) return DEFAULT_CHROME_HEIGHT;
  return Math.min(400, Math.max(0, Math.round(numeric)));
}
