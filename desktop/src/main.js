const page = document.querySelector("#page");
const welcome = document.querySelector("#welcome");
const address = document.querySelector("#address");
const homeAddress = document.querySelector("#home-address");
const status = document.querySelector("#status");
const back = document.querySelector("#back");
const forward = document.querySelector("#forward");

function normalizeDestination(raw) {
  const value = raw.trim();
  if (!value) return null;
  if (/^[a-z][a-z0-9+.-]*:\/\//i.test(value)) return value;
  if (/^(localhost|127\.0\.0\.1)(:\d+)?([/].*)?$/i.test(value)) return `http://${value}`;
  if (/^[^\s.]+$/.test(value) || !value.includes(".")) return `https://www.google.com/search?q=${encodeURIComponent(value)}`;
  return `https://${value}`;
}
function navigate(raw) {
  const target = normalizeDestination(raw);
  if (!target) return;
  address.value = target;
  homeAddress.value = target;
  welcome.hidden = true;
  page.style.display = "block";
  page.src = target;
  status.textContent = "Loading";
}
document.querySelector("#navigate").addEventListener("submit", event => { event.preventDefault(); navigate(address.value); });
document.querySelector("#home-search").addEventListener("submit", event => { event.preventDefault(); navigate(homeAddress.value); });
back.addEventListener("click", () => page.goBack());
forward.addEventListener("click", () => page.goForward());
document.querySelector("#reload").addEventListener("click", () => page.reload());
document.querySelector("#home").addEventListener("click", () => {
  page.src = "about:blank"; page.style.display = "none"; welcome.hidden = false;
  address.value = ""; status.textContent = "Home";
});
page.addEventListener("did-start-loading", () => { status.textContent = "Loading"; });
page.addEventListener("did-stop-loading", () => {
  status.textContent = "Ready"; back.disabled = !page.canGoBack(); forward.disabled = !page.canGoForward();
  try { address.value = page.getURL(); } catch { /* Keep current address during transient navigation. */ }
});
page.addEventListener("did-navigate", event => { address.value = event.url; homeAddress.value = event.url; });
page.addEventListener("did-fail-load", event => { if (event.errorCode !== -3) status.textContent = `Could not load page (${event.errorCode})`; });
