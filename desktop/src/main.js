import { normalizeDestination } from "./navigation.js";

const page = document.querySelector("#page");
const welcome = document.querySelector("#welcome");
const address = document.querySelector("#address");
const homeAddress = document.querySelector("#home-address");
const status = document.querySelector("#status");
const back = document.querySelector("#back");
const forward = document.querySelector("#forward");

function navigate(raw) {
  const target = normalizeDestination(raw);
  if (!target) { status.textContent = "Enter a valid address or search term"; return; }
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
  address.value = ""; back.disabled = true; forward.disabled = true; status.textContent = "Home";
});
page.addEventListener("did-start-loading", () => { status.textContent = "Loading"; });
page.addEventListener("did-stop-loading", () => {
  status.textContent = "Ready"; back.disabled = !page.canGoBack(); forward.disabled = !page.canGoForward();
  try { address.value = page.getURL(); } catch { /* Keep current address during transient navigation. */ }
});
page.addEventListener("did-navigate", event => { address.value = event.url; homeAddress.value = event.url; });
page.addEventListener("did-fail-load", event => { if (event.errorCode !== -3) status.textContent = `Could not load page (${event.errorCode})`; });

// Browser keyboard conventions: Cmd on macOS, Ctrl on Windows/Linux.
document.addEventListener("keydown", event => {
  const command = event.ctrlKey || event.metaKey;
  if (command && event.key.toLowerCase() === "l") {
    event.preventDefault(); address.focus(); address.select();
  } else if (command && event.key.toLowerCase() === "r") {
    event.preventDefault(); page.reload();
  } else if (event.altKey && event.key === "ArrowLeft" && !back.disabled) {
    event.preventDefault(); page.goBack();
  } else if (event.altKey && event.key === "ArrowRight" && !forward.disabled) {
    event.preventDefault(); page.goForward();
  }
});
