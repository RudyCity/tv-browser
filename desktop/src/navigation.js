export function normalizeDestination(raw) {
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
    return `https://www.google.com/search?q=${encodeURIComponent(value)}`;
  }

  try {
    const url = new URL(`https://${value}`);
    return url.hostname ? url.href : null;
  } catch { return null; }
}
