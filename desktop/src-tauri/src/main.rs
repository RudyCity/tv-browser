#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use std::{
    collections::HashMap,
    fs,
    path::{Path, PathBuf},
    process::Command,
    sync::Mutex,
};

use serde_json::{json, Value};
use tauri::{
    webview::{
        DownloadEvent, NewWindowResponse, PageLoadEvent, PermissionKind, PermissionResponse,
        WebviewBuilder,
    },
    Emitter, LogicalPosition, LogicalSize, Manager, WebviewUrl, Window, WindowEvent,
};
use url::Url;

mod downloads;

use downloads::DownloadManager;

const MAIN_UI_LABEL: &str = "main-ui";
const CONTENT_LABEL_PREFIX: &str = "browser-content-";
const STATUS_HEIGHT: f64 = 28.0;
const DEFAULT_CHROME_HEIGHT: f64 = 86.0;
const MIN_ZOOM: f64 = 0.5;
const MAX_ZOOM: f64 = 3.0;
const WINDOW_TITLE: &str = "Rudy Browser";

fn is_allowed_browser_url(url: &Url) -> bool {
    matches!(url.scheme(), "http" | "https")
        || (url.scheme() == "about" && url.as_str() == "about:blank")
}

fn content_label(id: u64) -> String {
    format!("{CONTENT_LABEL_PREFIX}{id}")
}

fn normalize_chrome_height(value: f64) -> f64 {
    if !value.is_finite() || value <= 0.0 {
        return DEFAULT_CHROME_HEIGHT;
    }
    value.min(400.0).round()
}

fn normalize_zoom(value: f64) -> f64 {
    if !value.is_finite() || value <= 0.0 {
        return 1.0;
    }
    let clamped = value.clamp(MIN_ZOOM, MAX_ZOOM);
    (clamped * 100.0).round() / 100.0
}

fn origin_of(url: &Url) -> Option<String> {
    if !matches!(url.scheme(), "http" | "https") {
        return None;
    }
    let host = url.host_str()?;
    match url.port() {
        Some(port) => Some(format!("{}://{}:{}", url.scheme(), host, port)),
        None => Some(format!("{}://{}", url.scheme(), host)),
    }
}

fn permission_kind_label(kind: &PermissionKind) -> &'static str {
    match kind {
        PermissionKind::Microphone => "microphone",
        PermissionKind::Camera => "camera",
        PermissionKind::Geolocation => "geolocation",
        PermissionKind::Notifications => "notifications",
        PermissionKind::ClipboardRead => "clipboard-read",
        PermissionKind::DisplayCapture => "display-capture",
        PermissionKind::Midi => "midi",
        PermissionKind::Sensors => "sensors",
        _ => "other",
    }
}

#[derive(Debug, Clone, PartialEq)]
struct BrowserLayout {
    window_width: f64,
    window_height: f64,
    content_top: f64,
    content_width: f64,
    content_height: f64,
}

fn calculate_content_layout(
    physical_width: u32,
    physical_height: u32,
    scale_factor: f64,
    chrome_height: f64,
) -> Result<BrowserLayout, String> {
    if !scale_factor.is_finite() || scale_factor <= 0.0 {
        return Err("Window scale factor must be a finite positive number".into());
    }

    let width = (f64::from(physical_width) / scale_factor).max(1.0);
    let height = (f64::from(physical_height) / scale_factor).max(1.0);
    let chrome = normalize_chrome_height(chrome_height);
    let content_top = chrome.min(height);
    let status_height = STATUS_HEIGHT.min((height - content_top).max(0.0));
    let content_height = (height - content_top - status_height).max(1.0);

    Ok(BrowserLayout {
        window_width: width,
        window_height: height,
        content_top,
        content_width: width,
        content_height,
    })
}

#[derive(Debug, Clone)]
struct TabInfo {
    id: u64,
    private: bool,
    title: String,
    url: String,
    zoom: f64,
}

impl TabInfo {
    fn to_json(&self) -> Value {
        json!({
            "id": self.id,
            "private": self.private,
            "title": self.title,
            "url": self.url,
            "zoom": self.zoom
        })
    }
}

#[derive(Default)]
struct TabCollection {
    tabs: Vec<TabInfo>,
    active: Option<u64>,
    next_id: u64,
}

impl TabCollection {
    fn find(&self, id: u64) -> Option<&TabInfo> {
        self.tabs.iter().find(|tab| tab.id == id)
    }

    fn find_mut(&mut self, id: u64) -> Option<&mut TabInfo> {
        self.tabs.iter_mut().find(|tab| tab.id == id)
    }

    /// Removes a tab and moves the active selection to its neighbour. Returns
    /// the number of tabs that remain.
    fn close(&mut self, id: u64) -> Result<usize, String> {
        let index = self
            .tabs
            .iter()
            .position(|tab| tab.id == id)
            .ok_or_else(|| "Tab no longer exists".to_string())?;
        self.tabs.remove(index);
        if self.active == Some(id) {
            let next_index = index.min(self.tabs.len().saturating_sub(1));
            self.active = self.tabs.get(next_index).map(|tab| tab.id);
        }
        Ok(self.tabs.len())
    }

    fn move_to(&mut self, id: u64, target_index: usize) -> Result<(), String> {
        let index = self
            .tabs
            .iter()
            .position(|tab| tab.id == id)
            .ok_or_else(|| "Tab no longer exists".to_string())?;
        let tab = self.tabs.remove(index);
        let clamped = target_index.min(self.tabs.len());
        self.tabs.insert(clamped, tab);
        Ok(())
    }
}

struct BrowserState {
    tabs: Mutex<TabCollection>,
    chrome_height: Mutex<f64>,
    permissions: Mutex<HashMap<String, Vec<String>>>,
}

impl Default for BrowserState {
    fn default() -> Self {
        Self {
            tabs: Mutex::new(TabCollection::default()),
            chrome_height: Mutex::new(DEFAULT_CHROME_HEIGHT),
            permissions: Mutex::new(HashMap::new()),
        }
    }
}

impl BrowserState {
    fn chrome_height(&self) -> f64 {
        self.chrome_height
            .lock()
            .map(|value| *value)
            .unwrap_or(DEFAULT_CHROME_HEIGHT)
    }

    fn tabs_payload(&self) -> Value {
        let tabs = match self.tabs.lock() {
            Ok(collection) => json!({
                "tabs": collection.tabs.iter().map(TabInfo::to_json).collect::<Vec<_>>(),
                "activeId": collection.active
            }),
            Err(error) => json!({
                "tabs": [],
                "activeId": null,
                "error": format!("Tab lock failed: {error}")
            }),
        };
        tabs
    }

    fn active_content(&self, app: &tauri::AppHandle) -> Option<tauri::webview::Webview> {
        let id = self
            .tabs
            .lock()
            .ok()
            .and_then(|collection| collection.active)?;
        app.get_webview(&content_label(id))
    }
}

fn emit_tabs(app: &tauri::AppHandle) {
    let payload = app.state::<BrowserState>().tabs_payload();
    let _ = app.emit("browser-tabs-changed", payload);
}

fn record_tab_url(app: &tauri::AppHandle, id: u64, url: &str) {
    if let Ok(mut collection) = app.state::<BrowserState>().tabs.lock() {
        if let Some(tab) = collection.find_mut(id) {
            tab.url = url.to_string();
        }
    }
}

fn record_tab_title(app: &tauri::AppHandle, id: u64, title: &str) {
    if let Ok(mut collection) = app.state::<BrowserState>().tabs.lock() {
        if let Some(tab) = collection.find_mut(id) {
            tab.title = title.to_string();
        }
    }
}

fn navigation_stack_state(
    webview: &tauri::webview::Webview,
    history: &NavigationHistory,
    tab_id: u64,
) -> Result<Value, String> {
    let current_url = webview.url().map_err(|error| error.to_string())?;
    history.record(tab_id, current_url.as_str())
}

#[derive(Default)]
struct NavigationHistory {
    stacks: Mutex<HashMap<u64, NavigationHistoryStack>>,
}

#[derive(Default)]
struct NavigationHistoryStack {
    entries: Vec<String>,
    index: usize,
}

impl NavigationHistoryStack {
    fn record(&mut self, url: &str) {
        if self.entries.is_empty() {
            self.entries.push(url.to_string());
            self.index = 0;
            return;
        }

        if self.entries.get(self.index).map(String::as_str) == Some(url) {
            return;
        }

        if self.index > 0 && self.entries[self.index - 1] == url {
            self.index -= 1;
            return;
        }

        if self.index + 1 < self.entries.len() && self.entries[self.index + 1] == url {
            self.index += 1;
            return;
        }

        self.entries.truncate(self.index + 1);
        self.entries.push(url.to_string());
        self.index = self.entries.len() - 1;
    }

    fn state(&self) -> Value {
        json!({
            "canGoBack": !self.entries.is_empty() && self.index > 0,
            "canGoForward": !self.entries.is_empty() && self.index + 1 < self.entries.len()
        })
    }
}

impl NavigationHistory {
    fn record(&self, tab_id: u64, raw_url: &str) -> Result<Value, String> {
        let parsed = match Url::parse(raw_url) {
            Ok(url) if matches!(url.scheme(), "http" | "https") => url,
            _ => return self.state(tab_id),
        };
        let mut stacks = self
            .stacks
            .lock()
            .map_err(|error| format!("Navigation history lock failed: {error}"))?;
        let stack = stacks.entry(tab_id).or_default();
        stack.record(parsed.as_str());
        Ok(stack.state())
    }

    fn state(&self, tab_id: u64) -> Result<Value, String> {
        let stacks = self
            .stacks
            .lock()
            .map_err(|error| format!("Navigation history lock failed: {error}"))?;
        Ok(stacks
            .get(&tab_id)
            .map(NavigationHistoryStack::state)
            .unwrap_or_else(|| NavigationHistoryStack::default().state()))
    }

    fn forget(&self, tab_id: u64) {
        if let Ok(mut stacks) = self.stacks.lock() {
            stacks.remove(&tab_id);
        }
    }
}

fn emit_navigation_state(app: &tauri::AppHandle, webview: &tauri::webview::Webview, tab_id: u64) {
    match navigation_stack_state(webview, app.state::<NavigationHistory>().inner(), tab_id) {
        Ok(state) => {
            let _ = app.emit("browser-navigation-state", state);
        }
        Err(error) => {
            let _ = app.emit("browser-navigation-state-error", error);
        }
    }
}

/// The native load events do not report failure, so a finished page is probed
/// in its own document. Chromium and WebKitGTK render failed navigations as an
/// internal error document, which is detected by its marker element.
const PAGE_PROBE_SCRIPT: &str = r#"(function () {
  try {
    var body = document.body;
    if (document.getElementById && document.getElementById("main-frame-error")) {
      return { failed: true, reason: "The page could not be loaded" };
    }
    if (!body) {
      return { failed: true, reason: "The page did not render any content" };
    }
    return { failed: false, text: String(body.innerText || "").length };
  } catch (error) {
    return { failed: true, reason: "The page did not finish loading" };
  }
})();"#;

fn parse_script_result(raw: &str) -> Option<Value> {
    let value: Value = match serde_json::from_str(raw.trim()) {
        Ok(parsed) => parsed,
        Err(_) => return None,
    };

    match value {
        Value::String(inner) => serde_json::from_str(&inner).ok(),
        other => Some(other),
    }
}

fn probe_page(app: &tauri::AppHandle, webview: &tauri::webview::Webview, tab_id: u64, url: String) {
    let handle = app.clone();
    let emitted_url = url.clone();
    let scheduled = webview.eval_with_callback(PAGE_PROBE_SCRIPT, move |result| {
        let parsed = parse_script_result(&result);
        let failed = parsed
            .as_ref()
            .and_then(|value| value.get("failed").and_then(Value::as_bool))
            .unwrap_or(false);
        let reason = parsed
            .as_ref()
            .and_then(|value| value.get("reason").and_then(Value::as_str))
            .unwrap_or("The page could not be loaded")
            .to_string();

        let _ = handle.emit(
            "browser-page-probe",
            json!({
                "tabId": tab_id,
                "url": emitted_url,
                "failed": failed,
                "reason": reason
            }),
        );
    });

    if let Err(error) = scheduled {
        let _ = app.emit(
            "browser-page-probe",
            json!({
                "tabId": tab_id,
                "url": url,
                "failed": true,
                "reason": format!("The page could not be inspected: {error}")
            }),
        );
    }
}

fn layout_content_views(app: &tauri::AppHandle, window: &Window) -> Result<(), String> {
    let scale_factor = window.scale_factor().map_err(|error| error.to_string())?;
    let physical_size = window.inner_size().map_err(|error| error.to_string())?;
    let layout = calculate_content_layout(
        physical_size.width,
        physical_size.height,
        scale_factor,
        app.state::<BrowserState>().chrome_height(),
    )?;

    if let Some(main_ui) = app.get_webview(MAIN_UI_LABEL) {
        main_ui
            .set_position(LogicalPosition::new(0.0, 0.0))
            .map_err(|error| error.to_string())?;
        main_ui
            .set_size(LogicalSize::new(layout.window_width, layout.window_height))
            .map_err(|error| error.to_string())?;
    }

    let ids: Vec<u64> = match app.state::<BrowserState>().tabs.lock() {
        Ok(collection) => collection.tabs.iter().map(|tab| tab.id).collect(),
        Err(error) => return Err(format!("Tab lock failed: {error}")),
    };

    for id in ids {
        if let Some(view) = app.get_webview(&content_label(id)) {
            view.set_position(LogicalPosition::new(0.0, layout.content_top))
                .map_err(|error| error.to_string())?;
            view.set_size(LogicalSize::new(
                layout.content_width,
                layout.content_height,
            ))
            .map_err(|error| error.to_string())?;
        }
    }

    Ok(())
}

fn permission_response(
    app: &tauri::AppHandle,
    webview: &tauri::webview::Webview,
    kind: &PermissionKind,
) -> PermissionResponse {
    let origin = webview.url().ok().and_then(|url| origin_of(&url));
    let label = permission_kind_label(kind);

    let allowed = match origin.as_deref() {
        Some(origin) => app
            .state::<BrowserState>()
            .permissions
            .lock()
            .map(|permissions| {
                permissions
                    .get(origin)
                    .map(|kinds| kinds.iter().any(|value| value == label))
                    .unwrap_or(false)
            })
            .unwrap_or(false),
        None => false,
    };

    if allowed {
        return PermissionResponse::Allow;
    }

    // The callback has to answer immediately, so the denial is reported to the
    // toolbar as an event instead of waiting for the user to decide.
    let url = webview
        .url()
        .ok()
        .map(|url| url.as_str().to_string())
        .unwrap_or_default();
    let _ = app.emit(
        "browser-permission-request",
        json!({
            "origin": origin.unwrap_or_default(),
            "kind": label,
            "url": url
        }),
    );
    PermissionResponse::Deny
}

fn build_content_webview(
    app: &tauri::AppHandle,
    window: &Window,
    id: u64,
    private: bool,
    chrome_height: f64,
) -> Result<tauri::webview::Webview, String> {
    let label = content_label(id);
    let initial_url = Url::parse("about:blank").map_err(|error| error.to_string())?;

    let load_handle = app.clone();
    let title_handle = app.clone();
    let title_window = window.clone();
    let navigation_handle = app.clone();
    let download_handle = app.clone();
    let popup_handle = app.clone();
    let permission_handle = app.clone();

    let download_directory = download_directory(app);
    fs::create_dir_all(&download_directory).map_err(|error| error.to_string())?;
    let download_dir = download_directory.clone();

    let mut builder = WebviewBuilder::new(&label, WebviewUrl::External(initial_url))
        .on_navigation(is_allowed_browser_url)
        .on_new_window(move |url, _features| {
            let popup_url = url.as_str().to_string();
            if !matches!(url.scheme(), "http" | "https") || url.host_str().is_none() {
                let _ = popup_handle.emit(
                    "browser-popup",
                    json!({
                        "tabId": id,
                        "opened": false,
                        "url": popup_url,
                        "reason": "Only HTTP and HTTPS popups can be opened"
                    }),
                );
                return NewWindowResponse::Deny;
            }

            let main_thread_handle = popup_handle.clone();
            let scheduling_handle = popup_handle.clone();
            let callback_popup_url = popup_url.clone();
            let scheduled = scheduling_handle.run_on_main_thread(move || {
                let state = main_thread_handle.state::<BrowserState>();

                // The popup belongs to the tab that requested it, even when the
                // user has switched to another tab by the time it is handled.
                let others: Vec<u64> = state
                    .tabs
                    .lock()
                    .map(|collection| {
                        collection
                            .tabs
                            .iter()
                            .filter(|tab| tab.id != id)
                            .map(|tab| tab.id)
                            .collect()
                    })
                    .unwrap_or_default();
                if let Ok(mut collection) = state.tabs.lock() {
                    if collection.find(id).is_some() {
                        collection.active = Some(id);
                    }
                }

                let navigation = match main_thread_handle.get_webview(&content_label(id)) {
                    Some(webview) => {
                        for other in others {
                            if let Some(view) =
                                main_thread_handle.get_webview(&content_label(other))
                            {
                                let _ = view.hide();
                            }
                        }
                        webview
                            .show()
                            .and_then(|()| webview.navigate(url))
                            .map_err(|error| error.to_string())
                    }
                    None => Err("The tab that requested this popup is no longer open".to_string()),
                };

                if navigation.is_ok() {
                    emit_tabs(&main_thread_handle);
                }

                let _ = main_thread_handle.emit(
                    "browser-popup",
                    json!({
                        "tabId": id,
                        "opened": navigation.is_ok(),
                        "url": callback_popup_url,
                        "error": navigation.err()
                    }),
                );
            });

            if let Err(error) = scheduled {
                let _ = popup_handle.emit(
                    "browser-popup",
                    json!({
                        "tabId": id,
                        "opened": false,
                        "url": popup_url,
                        "error": error.to_string()
                    }),
                );
            }

            NewWindowResponse::Deny
        })
        .on_permission_request(move |webview, kind| {
            permission_response(&permission_handle, &webview, &kind)
        })
        .on_page_load(move |webview, payload| {
            let phase = match payload.event() {
                PageLoadEvent::Started => "started",
                PageLoadEvent::Finished => "finished",
            };
            let url = payload.url().as_str().to_string();
            record_tab_url(&load_handle, id, &url);
            let _ = load_handle.emit(
                "browser-load",
                json!({
                    "tabId": id,
                    "event": phase,
                    "url": url
                }),
            );
            let tabs_payload = navigation_handle.state::<BrowserState>().tabs_payload();
            let _ = navigation_handle.emit("browser-tabs-changed", tabs_payload);
            emit_navigation_state(&navigation_handle, &webview, id);
            if matches!(payload.event(), PageLoadEvent::Finished) {
                probe_page(&navigation_handle, &webview, id, url.clone());
            }
        })
        .on_document_title_changed(move |_webview, title| {
            let trimmed = title.trim().to_string();
            record_tab_title(&title_handle, id, &trimmed);
            let window_title = if trimmed.is_empty() {
                WINDOW_TITLE.to_string()
            } else {
                format!("{trimmed} - {WINDOW_TITLE}")
            };
            let _ = title_window.set_title(&window_title);
            let _ = title_handle.emit(
                "browser-tab-title",
                json!({
                    "tabId": id,
                    "title": trimmed
                }),
            );
        })
        .on_download(move |_webview, event| {
            if let DownloadEvent::Requested { url, destination } = event {
                *destination = unique_download_path(&download_dir, &url);
                let manager = download_handle.state::<DownloadManager>();
                let started = manager.start(&download_handle, id, &url, destination.clone());
                if started.is_err() {
                    // The native download is refused below, so the toolbar has
                    // to learn about the failure through the existing event.
                    let _ = download_handle.emit(
                        "browser-download-finished",
                        json!({
                            "tabId": id,
                            "url": url.as_str(),
                            "path": destination.to_string_lossy(),
                            "success": false
                        }),
                    );
                }
                return false;
            }
            true
        });

    if private {
        builder = builder.incognito(true);
    }

    let scale_factor = window.scale_factor().map_err(|error| error.to_string())?;
    let physical_size = window.inner_size().map_err(|error| error.to_string())?;
    let layout = calculate_content_layout(
        physical_size.width,
        physical_size.height,
        scale_factor,
        chrome_height,
    )?;

    let view = window
        .add_child(
            builder,
            LogicalPosition::new(0.0, layout.content_top),
            LogicalSize::new(layout.content_width, layout.content_height),
        )
        .map_err(|error| error.to_string())?;
    view.hide().map_err(|error| error.to_string())?;

    Ok(view)
}

fn download_directory(app: &tauri::AppHandle) -> PathBuf {
    app.path()
        .download_dir()
        .unwrap_or_else(|_| std::env::temp_dir().join("Rudy Browser Downloads"))
}

fn download_filename(url: &Url) -> String {
    let raw_name = url
        .path_segments()
        .and_then(|mut segments| segments.rfind(|part| !part.is_empty()))
        .unwrap_or("download.bin");

    let sanitized: String = raw_name
        .chars()
        .map(|character| {
            if character.is_ascii_alphanumeric() || matches!(character, '.' | '-' | '_') {
                character
            } else {
                '_'
            }
        })
        .collect();

    if sanitized.is_empty() || sanitized == "." || sanitized == ".." {
        "download.bin".into()
    } else {
        sanitized
    }
}

fn unique_download_path(directory: &Path, url: &Url) -> PathBuf {
    let file_name = download_filename(url);
    let candidate = directory.join(&file_name);
    if !candidate.exists() {
        return candidate;
    }

    let path = Path::new(&file_name);
    let stem = path
        .file_stem()
        .and_then(|value| value.to_str())
        .unwrap_or("download");
    let extension = path.extension().and_then(|value| value.to_str());

    for suffix in 1..10_000 {
        let name = match extension {
            Some(extension) => format!("{stem} ({suffix}).{extension}"),
            None => format!("{stem} ({suffix})"),
        };
        let candidate = directory.join(name);
        if !candidate.exists() {
            return candidate;
        }
    }

    directory.join(format!("download-{}.bin", std::process::id()))
}

fn find_count_script(term: &str, forward: bool) -> Result<String, String> {
    let term_literal = serde_json::to_string(term).map_err(|error| error.to_string())?;
    let backwards = if forward { "false" } else { "true" };

    Ok(format!(
        r#"(function () {{
  try {{
    var term = {term_literal};
    var count = 0;
    if (term && document.body) {{
      var needle = term.toLowerCase();
      var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null);
      var node;
      while ((node = walker.nextNode())) {{
        var value = node.nodeValue;
        if (!value) {{ continue; }}
        var haystack = value.toLowerCase();
        var index = haystack.indexOf(needle);
        while (index !== -1) {{
          count += 1;
          index = haystack.indexOf(needle, index + needle.length);
        }}
      }}
    }}
    var found = false;
    try {{ found = window.find(term, false, {backwards}, true); }} catch (error) {{ found = false; }}
    return {{ count: count, found: found }};
  }} catch (error) {{
    return {{ count: 0, found: false }};
  }}
}})();"#
    ))
}

fn parse_find_result(raw: &str) -> (u64, bool) {
    let value = match parse_script_result(raw) {
        Some(parsed) => parsed,
        None => return (0, false),
    };

    let count = value.get("count").and_then(Value::as_u64).unwrap_or(0);
    let found = value.get("found").and_then(Value::as_bool).unwrap_or(false);
    (count, found)
}

#[tauri::command(async)]
fn browser_layout(app: tauri::AppHandle, window: Window, chrome_height: f64) -> Result<(), String> {
    let normalized = normalize_chrome_height(chrome_height);
    if let Ok(mut stored) = app.state::<BrowserState>().chrome_height.lock() {
        *stored = normalized;
    }
    layout_content_views(&app, &window)
}

#[tauri::command]
fn browser_tab_list(app: tauri::AppHandle) -> Value {
    app.state::<BrowserState>().tabs_payload()
}

// Run off the WebResourceRequested UI thread on Windows so Window::add_child
// does not re-entrantly pump COM/WebView2 initialization inside an IPC callback.
#[tauri::command(async)]
fn browser_tab_new(
    app: tauri::AppHandle,
    window: Window,
    url: Option<String>,
    incognito: bool,
    title: Option<String>,
    zoom: Option<f64>,
) -> Result<Value, String> {
    let state = app.state::<BrowserState>();
    let chrome_height = state.chrome_height();

    let id = {
        let mut collection = state
            .tabs
            .lock()
            .map_err(|error| format!("Tab lock failed: {error}"))?;
        let id = collection.next_id;
        collection.next_id += 1;
        id
    };

    let view = build_content_webview(&app, &window, id, incognito, chrome_height)?;

    let target = match url {
        Some(raw) if !raw.trim().is_empty() => {
            let parsed = Url::parse(&raw).map_err(|error| format!("Invalid address: {error}"))?;
            if !is_allowed_browser_url(&parsed) {
                let _ = view.close();
                return Err("Only HTTP and HTTPS websites are allowed".into());
            }
            Some(parsed)
        }
        _ => None,
    };

    {
        let mut collection = state
            .tabs
            .lock()
            .map_err(|error| format!("Tab lock failed: {error}"))?;
        collection.tabs.push(TabInfo {
            id,
            private: incognito,
            title: title.unwrap_or_default(),
            url: target
                .as_ref()
                .map(|value| value.as_str().to_string())
                .unwrap_or_default(),
            zoom: normalize_zoom(zoom.unwrap_or(1.0)),
        });
        collection.active = Some(id);
    }

    if let Some(parsed) = target {
        view.show().map_err(|error| error.to_string())?;
        view.navigate(parsed).map_err(|error| error.to_string())?;
    }

    let payload = state.tabs_payload();
    emit_tabs(&app);
    Ok(payload)
}

#[tauri::command(async)]
fn browser_tab_activate(app: tauri::AppHandle, id: u64) -> Result<Value, String> {
    let state = app.state::<BrowserState>();

    let (target_url, others) = {
        let mut collection = state
            .tabs
            .lock()
            .map_err(|error| format!("Tab lock failed: {error}"))?;
        let target_url = collection
            .find(id)
            .map(|tab| tab.url.clone())
            .ok_or_else(|| "Tab no longer exists".to_string())?;
        let others: Vec<u64> = collection
            .tabs
            .iter()
            .filter(|tab| tab.id != id)
            .map(|tab| tab.id)
            .collect();
        collection.active = Some(id);
        (target_url, others)
    };

    for other in others {
        if let Some(view) = app.get_webview(&content_label(other)) {
            let _ = view.hide();
        }
    }

    if let Some(view) = app.get_webview(&content_label(id)) {
        if target_url.is_empty() {
            let _ = view.hide();
        } else {
            view.show().map_err(|error| error.to_string())?;
            let _ = view.set_zoom(state_zoom(&app, id));
            emit_navigation_state(&app, &view, id);
        }
    }

    let payload = state.tabs_payload();
    emit_tabs(&app);
    Ok(payload)
}

fn state_zoom(app: &tauri::AppHandle, id: u64) -> f64 {
    app.state::<BrowserState>()
        .tabs
        .lock()
        .ok()
        .and_then(|collection| collection.find(id).map(|tab| tab.zoom))
        .unwrap_or(1.0)
}

#[tauri::command(async)]
fn browser_tab_close(app: tauri::AppHandle, window: Window, id: u64) -> Result<Value, String> {
    let state = app.state::<BrowserState>();

    let remaining = {
        let mut collection = state
            .tabs
            .lock()
            .map_err(|error| format!("Tab lock failed: {error}"))?;
        collection.close(id)?
    };

    if let Some(view) = app.get_webview(&content_label(id)) {
        let _ = view.close();
    }
    app.state::<NavigationHistory>().forget(id);

    if remaining == 0 {
        let new_id = {
            let mut collection = state
                .tabs
                .lock()
                .map_err(|error| format!("Tab lock failed: {error}"))?;
            let new_id = collection.next_id;
            collection.next_id += 1;
            new_id
        };

        let view = build_content_webview(&app, &window, new_id, false, state.chrome_height())?;
        let _ = view.hide();

        let mut collection = state
            .tabs
            .lock()
            .map_err(|error| format!("Tab lock failed: {error}"))?;
        collection.tabs.push(TabInfo {
            id: new_id,
            private: false,
            title: String::new(),
            url: String::new(),
            zoom: 1.0,
        });
        collection.active = Some(new_id);
    }

    let payload = state.tabs_payload();
    emit_tabs(&app);
    Ok(payload)
}

#[tauri::command]
fn browser_tab_move(app: tauri::AppHandle, id: u64, index: usize) -> Result<Value, String> {
    let state = app.state::<BrowserState>();

    {
        let mut collection = state
            .tabs
            .lock()
            .map_err(|error| format!("Tab lock failed: {error}"))?;
        collection.move_to(id, index)?;
    }

    let payload = state.tabs_payload();
    emit_tabs(&app);
    Ok(payload)
}

#[tauri::command(async)]
fn browser_navigate(app: tauri::AppHandle, window: Window, url: String) -> Result<(), String> {
    let parsed = Url::parse(&url).map_err(|error| format!("Invalid address: {error}"))?;
    if !is_allowed_browser_url(&parsed) {
        return Err("Only HTTP and HTTPS websites are allowed".into());
    }

    let state = app.state::<BrowserState>();
    let has_active = state
        .tabs
        .lock()
        .map(|collection| collection.active.is_some())
        .unwrap_or(false);

    if !has_active {
        browser_tab_new(
            app.clone(),
            window,
            Some(parsed.as_str().to_string()),
            false,
            None,
            None,
        )?;
        return Ok(());
    }

    let webview = state
        .active_content(&app)
        .ok_or_else(|| "Browser content view is not ready".to_string())?;

    webview.show().map_err(|error| error.to_string())?;
    webview.navigate(parsed).map_err(|error| error.to_string())
}

#[tauri::command(async)]
fn browser_back(app: tauri::AppHandle) -> Result<(), String> {
    let state = app.state::<BrowserState>();
    let tab_id = state
        .tabs
        .lock()
        .ok()
        .and_then(|collection| collection.active)
        .ok_or_else(|| "No active tab".to_string())?;
    let webview = state
        .active_content(&app)
        .ok_or_else(|| "No active tab".to_string())?;
    let history = app.state::<NavigationHistory>();
    let current = navigation_stack_state(&webview, history.inner(), tab_id)?;
    if current["canGoBack"].as_bool().unwrap_or(false) {
        webview
            .eval("window.history.back();")
            .map_err(|error| error.to_string())?;
    }
    Ok(())
}

#[tauri::command(async)]
fn browser_forward(app: tauri::AppHandle) -> Result<(), String> {
    let state = app.state::<BrowserState>();
    let tab_id = state
        .tabs
        .lock()
        .ok()
        .and_then(|collection| collection.active)
        .ok_or_else(|| "No active tab".to_string())?;
    let webview = state
        .active_content(&app)
        .ok_or_else(|| "No active tab".to_string())?;
    let history = app.state::<NavigationHistory>();
    let current = navigation_stack_state(&webview, history.inner(), tab_id)?;
    if current["canGoForward"].as_bool().unwrap_or(false) {
        webview
            .eval("window.history.forward();")
            .map_err(|error| error.to_string())?;
    }
    Ok(())
}

#[tauri::command(async)]
fn browser_reload(app: tauri::AppHandle) -> Result<(), String> {
    let state = app.state::<BrowserState>();
    let webview = state
        .active_content(&app)
        .ok_or_else(|| "No active tab".to_string())?;
    webview.reload().map_err(|error| error.to_string())
}

#[tauri::command(async)]
fn browser_home(app: tauri::AppHandle) -> Result<(), String> {
    let state = app.state::<BrowserState>();
    match state.active_content(&app) {
        Some(webview) => webview.hide().map_err(|error| error.to_string()),
        None => Ok(()),
    }
}

#[tauri::command(async)]
fn browser_navigation_state(app: tauri::AppHandle) -> Result<Value, String> {
    let state = app.state::<BrowserState>();
    let tab_id = state
        .tabs
        .lock()
        .ok()
        .and_then(|collection| collection.active)
        .ok_or_else(|| "No active tab".to_string())?;
    let webview = state
        .active_content(&app)
        .ok_or_else(|| "No active tab".to_string())?;
    let history = app.state::<NavigationHistory>();
    navigation_stack_state(&webview, history.inner(), tab_id)
}

#[tauri::command(async)]
fn browser_set_zoom(app: tauri::AppHandle, zoom: f64) -> Result<f64, String> {
    let normalized = normalize_zoom(zoom);
    let state = app.state::<BrowserState>();
    let webview = state
        .active_content(&app)
        .ok_or_else(|| "No active tab".to_string())?;

    webview
        .set_zoom(normalized)
        .map_err(|error| error.to_string())?;

    if let Ok(mut collection) = state.tabs.lock() {
        if let Some(id) = collection.active {
            if let Some(tab) = collection.find_mut(id) {
                tab.zoom = normalized;
            }
        }
    }

    Ok(normalized)
}

#[tauri::command]
fn browser_get_zoom(app: tauri::AppHandle) -> Result<f64, String> {
    let active = app
        .state::<BrowserState>()
        .tabs
        .lock()
        .ok()
        .and_then(|collection| collection.active);
    Ok(active.map(|id| state_zoom(&app, id)).unwrap_or(1.0))
}

#[tauri::command(async)]
fn browser_find(app: tauri::AppHandle, term: String, forward: bool) -> Result<(), String> {
    let trimmed = term.trim();
    if trimmed.is_empty() {
        return Ok(());
    }

    let state = app.state::<BrowserState>();
    let webview = state
        .active_content(&app)
        .ok_or_else(|| "No active page to search".to_string())?;
    let tab_id = state
        .tabs
        .lock()
        .ok()
        .and_then(|collection| collection.active)
        .unwrap_or(0);

    let script = find_count_script(trimmed, forward)?;
    let handle = app.clone();
    webview
        .eval_with_callback(script, move |result| {
            let (count, found) = parse_find_result(&result);
            let _ = handle.emit(
                "browser-find-result",
                json!({
                    "tabId": tab_id,
                    "count": count,
                    "found": found
                }),
            );
        })
        .map_err(|error| error.to_string())
}

#[tauri::command]
fn browser_permissions_set(
    app: tauri::AppHandle,
    permissions: HashMap<String, Vec<String>>,
) -> Result<(), String> {
    let state = app.state::<BrowserState>();
    let mut stored = state
        .permissions
        .lock()
        .map_err(|error| format!("Permission lock failed: {error}"))?;
    *stored = permissions;
    Ok(())
}

#[tauri::command(async)]
fn browser_clear_site_data(app: tauri::AppHandle) -> Result<(), String> {
    let ids: Vec<u64> = app
        .state::<BrowserState>()
        .tabs
        .lock()
        .map(|collection| collection.tabs.iter().map(|tab| tab.id).collect())
        .map_err(|error| format!("Tab lock failed: {error}"))?;

    let mut cleared = 0;
    let mut last_error = None;
    for id in ids {
        if let Some(view) = app.get_webview(&content_label(id)) {
            match view.clear_all_browsing_data() {
                Ok(()) => cleared += 1,
                Err(error) => last_error = Some(error.to_string()),
            }
        }
    }

    match last_error {
        Some(error) if cleared == 0 => Err(error),
        _ => Ok(()),
    }
}

#[tauri::command]
fn browser_open_path(path: String) -> Result<(), String> {
    let target = PathBuf::from(path.trim());
    if target.as_os_str().is_empty() {
        return Err("No path to open".into());
    }
    if !target.exists() {
        return Err("That path no longer exists".into());
    }

    #[cfg(target_os = "windows")]
    let mut command = {
        let mut command = Command::new("explorer");
        command.arg(&target);
        command
    };

    #[cfg(target_os = "macos")]
    let mut command = {
        let mut command = Command::new("open");
        command.arg(&target);
        command
    };

    #[cfg(all(unix, not(target_os = "macos")))]
    let mut command = {
        let mut command = Command::new("xdg-open");
        command.arg(&target);
        command
    };

    command
        .spawn()
        .map(|_| ())
        .map_err(|error| format!("Could not open the path: {error}"))
}

#[tauri::command(async)]
fn browser_toggle_fullscreen(window: Window) -> Result<bool, String> {
    let next = !window.is_fullscreen().map_err(|error| error.to_string())?;
    window
        .set_fullscreen(next)
        .map_err(|error| error.to_string())?;
    Ok(next)
}

fn main() {
    tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![
            browser_layout,
            browser_tab_list,
            browser_tab_new,
            browser_tab_activate,
            browser_tab_close,
            browser_tab_move,
            browser_navigate,
            browser_back,
            browser_forward,
            browser_reload,
            browser_home,
            browser_navigation_state,
            browser_set_zoom,
            browser_get_zoom,
            browser_find,
            browser_permissions_set,
            browser_clear_site_data,
            browser_open_path,
            browser_toggle_fullscreen,
            downloads::browser_download_list,
            downloads::browser_download_pause,
            downloads::browser_download_resume,
            downloads::browser_download_cancel
        ])
        .setup(|app| {
            app.manage(BrowserState::default());
            app.manage(NavigationHistory::default());
            app.manage(DownloadManager::default());

            let window = tauri::Window::builder(app, "main")
                .title(WINDOW_TITLE)
                .inner_size(1280.0, 820.0)
                .min_inner_size(720.0, 520.0)
                .background_color(tauri::window::Color(0x0C, 0x13, 0x1E, 0xFF))
                .center()
                .build()?;

            let scale_factor = window.scale_factor()?;
            let physical_size = window.inner_size()?;
            let logical_size = physical_size.to_logical::<f64>(scale_factor);

            // The local UI is created first. Page webviews are added per tab and
            // always overlay only the content area, never the toolbar or status bar.
            let ui_url = if cfg!(debug_assertions) {
                WebviewUrl::External("http://127.0.0.1:1420".parse()?)
            } else {
                WebviewUrl::App("index.html".into())
            };
            window.add_child(
                WebviewBuilder::new(MAIN_UI_LABEL, ui_url)
                    .auto_resize()
                    .background_color(tauri::window::Color(0x0C, 0x13, 0x1E, 0xFF)),
                LogicalPosition::new(0.0, 0.0),
                LogicalSize::new(logical_size.width, logical_size.height),
            )?;

            let layout_app = app.handle().clone();
            let layout_window = window.clone();
            window.on_window_event(move |event| match event {
                WindowEvent::Resized(_) | WindowEvent::ScaleFactorChanged { .. } => {
                    let app_handle = layout_app.clone();
                    let event_window = layout_window.clone();
                    let scheduling_handle = layout_app.clone();
                    if let Err(error) = scheduling_handle.run_on_main_thread(move || {
                        if let Err(layout_error) = layout_content_views(&app_handle, &event_window)
                        {
                            let _ = app_handle.emit("browser-layout-error", layout_error);
                        }
                    }) {
                        let _ = layout_app.emit("browser-layout-error", error.to_string());
                    }
                }
                WindowEvent::CloseRequested { api, .. } => {
                    // Tauri's multi-webview event loop can stay blocked after
                    // RequestExit on Windows when child WebView2 controllers are active.
                    api.prevent_close();
                    let _ = layout_window.hide();
                    std::process::exit(0);
                }
                _ => {}
            });

            Ok(())
        })
        .run(tauri::generate_context!())
        .expect("error while running Rudy Browser");
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn content_layout_uses_logical_bounds_at_one_x_scale() {
        let layout = calculate_content_layout(1280, 800, 1.0, 120.0).unwrap();

        assert_eq!(layout.window_width, 1280.0);
        assert_eq!(layout.window_height, 800.0);
        assert_eq!(layout.content_width, 1280.0);
        assert_eq!(layout.content_top, 120.0);
        assert_eq!(layout.content_height, 800.0 - 120.0 - STATUS_HEIGHT);
    }

    #[test]
    fn content_layout_converts_physical_bounds_for_high_dpi() {
        let layout = calculate_content_layout(2560, 1600, 2.0, 120.0).unwrap();

        assert_eq!(layout.window_width, 1280.0);
        assert_eq!(layout.window_height, 800.0);
        assert_eq!(layout.content_width, 1280.0);
        assert_eq!(layout.content_top, 120.0);
        assert_eq!(layout.content_height, 800.0 - 120.0 - STATUS_HEIGHT);
    }

    #[test]
    fn content_layout_rejects_invalid_scale_factor() {
        assert!(calculate_content_layout(1280, 800, 0.0, 120.0).is_err());
        assert!(calculate_content_layout(1280, 800, f64::NAN, 120.0).is_err());
    }

    #[test]
    fn content_layout_keeps_a_nonzero_content_extent_for_tiny_windows() {
        let layout = calculate_content_layout(20, 20, 1.0, 120.0).unwrap();

        assert!(layout.content_width >= 1.0);
        assert!(layout.content_height >= 1.0);
        assert!(layout.content_top <= 120.0);
    }

    #[test]
    fn content_layout_falls_back_when_chrome_height_is_unusable() {
        let invalid = calculate_content_layout(1280, 800, 1.0, f64::NAN).unwrap();
        let zero = calculate_content_layout(1280, 800, 1.0, 0.0).unwrap();

        assert_eq!(invalid.content_top, DEFAULT_CHROME_HEIGHT);
        assert_eq!(zero.content_top, DEFAULT_CHROME_HEIGHT);
    }

    #[test]
    fn chrome_height_is_clamped_to_a_sane_range() {
        assert_eq!(normalize_chrome_height(120.0), 120.0);
        assert_eq!(normalize_chrome_height(120.4), 120.0);
        assert_eq!(normalize_chrome_height(-1.0), DEFAULT_CHROME_HEIGHT);
        assert_eq!(normalize_chrome_height(5000.0), 400.0);
    }

    #[test]
    fn zoom_is_clamped_to_the_supported_range() {
        assert_eq!(normalize_zoom(1.0), 1.0);
        assert_eq!(normalize_zoom(0.1), MIN_ZOOM);
        assert_eq!(normalize_zoom(12.0), MAX_ZOOM);
        assert_eq!(normalize_zoom(f64::NAN), 1.0);
        assert_eq!(normalize_zoom(1.254), 1.25);
    }

    #[test]
    fn only_http_https_and_blank_are_navigable() {
        assert!(is_allowed_browser_url(
            &Url::parse("https://example.com").unwrap()
        ));
        assert!(is_allowed_browser_url(
            &Url::parse("http://localhost:3000").unwrap()
        ));
        assert!(is_allowed_browser_url(&Url::parse("about:blank").unwrap()));
        assert!(!is_allowed_browser_url(
            &Url::parse("file:///etc/passwd").unwrap()
        ));
        assert!(!is_allowed_browser_url(
            &Url::parse("javascript:alert(1)").unwrap()
        ));
        assert!(!is_allowed_browser_url(
            &Url::parse("ftp://example.com").unwrap()
        ));
    }

    #[test]
    fn origins_are_reduced_to_scheme_host_and_port() {
        assert_eq!(
            origin_of(&Url::parse("https://example.com/path?q=1").unwrap()).as_deref(),
            Some("https://example.com")
        );
        assert_eq!(
            origin_of(&Url::parse("http://example.com:8080/a").unwrap()).as_deref(),
            Some("http://example.com:8080")
        );
        assert_eq!(origin_of(&Url::parse("about:blank").unwrap()), None);
        assert_eq!(origin_of(&Url::parse("file:///tmp/a").unwrap()), None);
    }

    #[test]
    fn content_labels_are_unique_per_tab() {
        assert_eq!(content_label(7), "browser-content-7");
        assert_ne!(content_label(7), content_label(8));
        assert_ne!(content_label(1), MAIN_UI_LABEL);
    }

    #[test]
    fn permission_kinds_map_to_stable_labels() {
        assert_eq!(permission_kind_label(&PermissionKind::Camera), "camera");
        assert_eq!(
            permission_kind_label(&PermissionKind::Microphone),
            "microphone"
        );
        assert_eq!(
            permission_kind_label(&PermissionKind::Geolocation),
            "geolocation"
        );
        assert_eq!(
            permission_kind_label(&PermissionKind::ClipboardRead),
            "clipboard-read"
        );
    }

    #[test]
    fn history_stack_tracks_back_and_forward() {
        let mut history = NavigationHistoryStack::default();

        history.record("https://example.com/one");
        assert_eq!(history.state()["canGoBack"], false);
        assert_eq!(history.state()["canGoForward"], false);

        history.record("https://example.com/two");
        assert_eq!(history.state()["canGoBack"], true);
        assert_eq!(history.state()["canGoForward"], false);

        history.record("https://example.com/one");
        assert_eq!(history.state()["canGoBack"], false);
        assert_eq!(history.state()["canGoForward"], true);

        history.record("https://example.com/two");
        assert_eq!(history.state()["canGoBack"], true);
        assert_eq!(history.state()["canGoForward"], false);
    }

    #[test]
    fn history_stack_truncates_forward_entries_after_new_navigation() {
        let mut history = NavigationHistoryStack::default();

        history.record("https://example.com/one");
        history.record("https://example.com/two");
        history.record("https://example.com/one");
        history.record("https://example.com/three");

        assert_eq!(history.state()["canGoBack"], true);
        assert_eq!(history.state()["canGoForward"], false);
    }

    #[test]
    fn history_stack_does_not_duplicate_the_current_url() {
        let mut history = NavigationHistoryStack::default();

        history.record("https://example.com/one");
        history.record("https://example.com/one");

        assert_eq!(history.entries.len(), 1);
        assert_eq!(history.state()["canGoBack"], false);
        assert_eq!(history.state()["canGoForward"], false);
    }

    #[test]
    fn navigation_history_is_isolated_per_tab() {
        let history = NavigationHistory::default();

        history.record(1, "https://example.com/one").unwrap();
        history.record(1, "https://example.com/two").unwrap();
        assert_eq!(history.state(1).unwrap()["canGoBack"], true);
        assert_eq!(history.state(2).unwrap()["canGoBack"], false);

        history.record(2, "https://other.example/a").unwrap();
        assert_eq!(history.state(2).unwrap()["canGoBack"], false);
        assert_eq!(history.state(1).unwrap()["canGoBack"], true);
    }

    #[test]
    fn forgetting_a_tab_drops_only_that_tab_history() {
        let history = NavigationHistory::default();

        history.record(1, "https://example.com/one").unwrap();
        history.record(1, "https://example.com/two").unwrap();
        history.record(2, "https://example.com/one").unwrap();
        history.record(2, "https://example.com/two").unwrap();

        history.forget(1);

        assert_eq!(history.state(1).unwrap()["canGoBack"], false);
        assert_eq!(history.state(2).unwrap()["canGoBack"], true);
    }

    #[test]
    fn navigation_state_ignores_non_web_urls() {
        let history = NavigationHistory::default();

        history.record(3, "about:blank").unwrap();
        history.record(3, "file:///tmp/secret").unwrap();

        assert_eq!(history.state(3).unwrap()["canGoBack"], false);
        assert_eq!(history.state(3).unwrap()["canGoForward"], false);
    }

    #[test]
    fn page_probe_script_detects_the_error_document_marker() {
        assert!(PAGE_PROBE_SCRIPT.contains("main-frame-error"));
        assert!(PAGE_PROBE_SCRIPT.contains("innerText"));
        assert!(PAGE_PROBE_SCRIPT.starts_with("(function"));
    }

    #[test]
    fn page_probe_result_parses_failures_and_double_encoding() {
        let failed = parse_script_result(r#"{"failed":true,"reason":"blocked"}"#).unwrap();
        assert_eq!(failed["failed"], true);
        assert_eq!(failed["reason"], "blocked");

        let encoded = parse_script_result(r#""{\"failed\":false,\"text\":12}""#).unwrap();
        assert_eq!(encoded["failed"], false);
        assert_eq!(encoded["text"], 12);

        assert!(parse_script_result("not json").is_none());
    }

    #[test]
    fn download_filename_is_sanitized() {
        let url = Url::parse("https://example.com/path/report%20final.pdf").unwrap();
        assert_eq!(download_filename(&url), "report_20final.pdf");
    }

    #[test]
    fn empty_download_filename_uses_safe_fallback() {
        let url = Url::parse("https://example.com/").unwrap();
        assert_eq!(download_filename(&url), "download.bin");
    }

    #[test]
    fn find_script_escapes_the_search_term() {
        let script = find_count_script("a\"b\\c", true).unwrap();
        assert!(script.contains(r#""a\"b\\c""#));
        assert!(script.contains("window.find(term, false, false, true)"));
    }

    #[test]
    fn find_result_parses_plain_and_double_encoded_payloads() {
        assert_eq!(parse_find_result(r#"{"count":3,"found":true}"#), (3, true));
        assert_eq!(
            parse_find_result(r#""{\"count\":2,\"found\":false}""#),
            (2, false)
        );
        assert_eq!(parse_find_result("not json"), (0, false));
        assert_eq!(parse_find_result(""), (0, false));
    }

    fn sample_tab(id: u64) -> TabInfo {
        TabInfo {
            id,
            private: false,
            title: format!("Tab {id}"),
            url: format!("https://example.com/{id}"),
            zoom: 1.0,
        }
    }

    fn tab_ids(collection: &TabCollection) -> Vec<u64> {
        collection.tabs.iter().map(|tab| tab.id).collect()
    }

    fn collection_with(tabs: Vec<TabInfo>, active: Option<u64>) -> TabCollection {
        TabCollection {
            next_id: tabs.iter().map(|tab| tab.id).max().unwrap_or(0) + 1,
            tabs,
            active,
        }
    }

    #[test]
    fn closing_the_active_tab_selects_its_neighbour() {
        let mut collection =
            collection_with(vec![sample_tab(0), sample_tab(1), sample_tab(2)], Some(1));

        assert_eq!(collection.close(1).unwrap(), 2);
        assert_eq!(collection.active, Some(2));
        assert_eq!(tab_ids(&collection), vec![0, 2]);
    }

    #[test]
    fn closing_the_last_tab_selects_the_previous_one() {
        let mut collection = collection_with(vec![sample_tab(0), sample_tab(1)], Some(1));

        collection.close(1).unwrap();
        assert_eq!(collection.active, Some(0));
    }

    #[test]
    fn closing_a_background_tab_keeps_the_active_tab() {
        let mut collection =
            collection_with(vec![sample_tab(0), sample_tab(1), sample_tab(2)], Some(0));

        collection.close(2).unwrap();
        assert_eq!(collection.active, Some(0));
        assert_eq!(tab_ids(&collection), vec![0, 1]);
    }

    #[test]
    fn closing_an_unknown_tab_is_rejected() {
        let mut collection = collection_with(vec![sample_tab(0)], Some(0));

        assert!(collection.close(9).is_err());
        assert_eq!(tab_ids(&collection), vec![0]);
    }

    #[test]
    fn moving_a_tab_reorders_without_touching_the_active_tab() {
        let mut collection =
            collection_with(vec![sample_tab(0), sample_tab(1), sample_tab(2)], Some(1));

        collection.move_to(2, 0).unwrap();
        assert_eq!(tab_ids(&collection), vec![2, 0, 1]);
        assert_eq!(collection.active, Some(1));

        collection.move_to(2, 99).unwrap();
        assert_eq!(tab_ids(&collection), vec![0, 1, 2]);
    }

    #[test]
    fn moving_an_unknown_tab_is_rejected() {
        let mut collection = collection_with(vec![sample_tab(0)], None);

        assert!(collection.move_to(5, 0).is_err());
        assert_eq!(tab_ids(&collection), vec![0]);
    }
}
