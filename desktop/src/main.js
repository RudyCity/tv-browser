import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { normalizeDestination } from "./navigation.js";

const address = document.querySelector("#address");
const homeAddress = document.querySelector("#home-address");
const welcome = document.querySelector("#welcome");
const status = document.querySelector("#status");
const backButton = document.querySelector("#back");
const forwardButton = document.querySelector("#forward");

async function runCommand(name, args) {
  try {
    return await invoke(name, args);
  } catch (error) {
    status.textContent = String(error);
    return undefined;
  }
}

async function navigate(raw) {
  const target = normalizeDestination(raw);
  if (!target) {
    status.textContent = "Enter a valid address or search term";
    return;
  }

  address.value = target;
  homeAddress.value = target;
  welcome.hidden = true;

  const result = await runCommand("browser_navigate", { url: target });
  if (result !== undefined) status.textContent = "Loading";
}

document.querySelector("#navigate").addEventListener("submit", event => {
  event.preventDefault();
  void navigate(address.value);
});

document.querySelector("#home-search").addEventListener("submit", event => {
  event.preventDefault();
  void navigate(homeAddress.value);
});

backButton.addEventListener("click", () => void runCommand("browser_back"));
forwardButton.addEventListener("click", () => void runCommand("browser_forward"));
document.querySelector("#reload").addEventListener("click", () => void runCommand("browser_reload"));

document.querySelector("#home").addEventListener("click", async () => {
  await runCommand("browser_home");
  welcome.hidden = false;
  address.value = "";
  backButton.disabled = true;
  forwardButton.disabled = true;
  status.textContent = "Home";
});

listen("browser-load", event => {
  const payload = event.payload;
  if (payload.event === "started") {
    welcome.hidden = true;
    status.textContent = "Loading";
    address.value = payload.url;
    homeAddress.value = payload.url;
    return;
  }

  status.textContent = "Ready";
  address.value = payload.url;
  homeAddress.value = payload.url;
  backButton.disabled = false;
  forwardButton.disabled = false;
}).catch(error => {
  status.textContent = "Browser event listener unavailable: " + String(error);
});

listen("browser-title", event => {
  const title = String(event.payload || "").trim();
  document.title = title ? title + " — Rudy Browser" : "Rudy Browser";
}).catch(error => console.error("Title listener unavailable", error));

listen("browser-download-finished", event => {
  const payload = event.payload;
  status.textContent = payload.success
    ? "Downloaded: " + (payload.path || "file saved")
    : "Download failed";
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
  if (command && event.key.toLowerCase() === "l") {
    event.preventDefault();
    address.focus();
    address.select();
  } else if (command && event.key.toLowerCase() === "r") {
    event.preventDefault();
    void runCommand("browser_reload");
  } else if (event.altKey && event.key === "ArrowLeft" && !backButton.disabled) {
    event.preventDefault();
    void runCommand("browser_back");
  } else if (event.altKey && event.key === "ArrowRight" && !forwardButton.disabled) {
    event.preventDefault();
    void runCommand("browser_forward");
  }
});
