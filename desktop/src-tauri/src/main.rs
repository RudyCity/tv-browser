use std::{
    fs,
    path::{Path, PathBuf},
    sync::Mutex,
};

use serde_json::json;
use tauri::{
    webview::{
        DownloadEvent, NewWindowResponse, PageLoadEvent, PermissionResponse, WebviewBuilder,
    },
    Emitter, LogicalPosition, LogicalSize, Manager, WebviewUrl, Window, WindowEvent,
};
use url::Url;

const MAIN_UI_LABEL: &str = "main-ui";
const BROWSER_CONTENT_LABEL: &str = "browser-content";
const TOOLBAR_HEIGHT: f64 = 68.0;
const STATUS_HEIGHT: f64 = 28.0;

fn is_allowed_browser_url(url: &Url) -> bool {
    matches!(url.scheme(), "http" | "https")
        || (url.scheme() == "about" && url.as_str() == "about:blank")
}

fn content_webview(app: &tauri::AppHandle) -> Result<tauri::webview::Webview, String> {
    app.get_webview(BROWSER_CONTENT_LABEL)
        .ok_or_else(|| "Browser content webview is not ready".to_string())
}

fn calculate_browser_layout(
    physical_width: u32,
    physical_height: u32,
    scale_factor: f64,
) -> Result<BrowserLayout, String> {
    if !scale_factor.is_finite() || scale_factor <= 0.0 {
        return Err("Window scale factor must be a finite positive number".into());
    }

    let width = (f64::from(physical_width) / scale_factor).max(1.0);
    let height = (f64::from(physical_height) / scale_factor).max(1.0);
    let content_top = TOOLBAR_HEIGHT.min(height);
    let status_height = STATUS_HEIGHT.min((height - content_top).max(0.0));
    let content_height = (height - content_top - status_height).max(1.0);

    Ok(BrowserLayout {
        window_width: width,
        window_height: height,
        content_top,
        content_height,
    })
}

#[derive(Debug, Clone, Copy, PartialEq)]
struct BrowserLayout {
    window_width: f64,
    window_height: f64,
    content_top: f64,
    content_height: f64,
}

fn layout_browser_views(app: &tauri::AppHandle, window: &Window) -> Result<(), String> {
    let scale_factor = window.scale_factor().map_err(|error| error.to_string())?;
    let physical_size = window.inner_size().map_err(|error| error.to_string())?;
    let layout = calculate_browser_layout(physical_size.width, physical_size.height, scale_factor)?;

    let main_ui = app
        .get_webview(MAIN_UI_LABEL)
        .ok_or_else(|| "Main UI webview is not ready".to_string())?;
    main_ui
        .set_position(LogicalPosition::new(0.0, 0.0))
        .map_err(|error| error.to_string())?;
    main_ui
        .set_size(LogicalSize::new(layout.window_width, layout.window_height))
        .map_err(|error| error.to_string())?;

    let content = content_webview(app)?;
    content
        .set_position(LogicalPosition::new(0.0, layout.content_top))
        .map_err(|error| error.to_string())?;
    content
        .set_size(LogicalSize::new(layout.window_width, layout.content_height))
        .map_err(|error| error.to_string())
}

#[tauri::command]
fn browser_navigate(app: tauri::AppHandle, url: String) -> Result<(), String> {
    let parsed = Url::parse(&url).map_err(|error| format!("Invalid address: {error}"))?;
    if !is_allowed_browser_url(&parsed) {
        return Err("Only HTTP and HTTPS websites are allowed".into());
    }

    let webview = content_webview(&app)?;
    webview.show().map_err(|error| error.to_string())?;
    webview.navigate(parsed).map_err(|error| error.to_string())
}

#[derive(Default)]
struct NavigationHistory {
    stack: Mutex<NavigationHistoryStack>,
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

    fn state(&self) -> serde_json::Value {
        json!({
            "canGoBack": !self.entries.is_empty() && self.index > 0,
            "canGoForward": !self.entries.is_empty() && self.index + 1 < self.entries.len()
        })
    }
}

impl NavigationHistory {
    fn record(&self, raw_url: &str) -> Result<serde_json::Value, String> {
        let parsed = match Url::parse(raw_url) {
            Ok(url) if matches!(url.scheme(), "http" | "https") => url,
            _ => return self.state(),
        };
        let mut stack = self
            .stack
            .lock()
            .map_err(|error| format!("Navigation history lock failed: {error}"))?;
        stack.record(parsed.as_str());
        Ok(stack.state())
    }

    fn state(&self) -> Result<serde_json::Value, String> {
        let stack = self
            .stack
            .lock()
            .map_err(|error| format!("Navigation history lock failed: {error}"))?;
        Ok(stack.state())
    }
}

fn navigation_state(
    webview: &tauri::webview::Webview,
    history: &NavigationHistory,
) -> Result<serde_json::Value, String> {
    let current_url = webview.url().map_err(|error| error.to_string())?;
    history.record(current_url.as_str())
}

#[tauri::command]
fn browser_navigation_state(app: tauri::AppHandle) -> Result<serde_json::Value, String> {
    let webview = content_webview(&app)?;
    let history = app.state::<NavigationHistory>();
    navigation_state(&webview, history.inner())
}

#[tauri::command]
fn browser_back(app: tauri::AppHandle) -> Result<(), String> {
    let webview = content_webview(&app)?;
    let history = app.state::<NavigationHistory>();
    let state = navigation_state(&webview, history.inner())?;
    if state["canGoBack"].as_bool().unwrap_or(false) {
        webview
            .eval("window.history.back();")
            .map_err(|error| error.to_string())?;
    }
    Ok(())
}

#[tauri::command]
fn browser_forward(app: tauri::AppHandle) -> Result<(), String> {
    let webview = content_webview(&app)?;
    let history = app.state::<NavigationHistory>();
    let state = navigation_state(&webview, history.inner())?;
    if state["canGoForward"].as_bool().unwrap_or(false) {
        webview
            .eval("window.history.forward();")
            .map_err(|error| error.to_string())?;
    }
    Ok(())
}

#[tauri::command]
fn browser_reload(app: tauri::AppHandle) -> Result<(), String> {
    content_webview(&app)?
        .reload()
        .map_err(|error| error.to_string())
}

#[tauri::command]
fn browser_home(app: tauri::AppHandle) -> Result<(), String> {
    content_webview(&app)?
        .hide()
        .map_err(|error| error.to_string())
}

#[tauri::command]
fn browser_current_url(app: tauri::AppHandle) -> Result<String, String> {
    content_webview(&app)?
        .url()
        .map(|url| url.to_string())
        .map_err(|error| error.to_string())
}

#[tauri::command]
fn browser_layout(app: tauri::AppHandle, window: Window) -> Result<(), String> {
    layout_browser_views(&app, &window)
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

fn main() {
    tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![
            browser_navigate,
            browser_back,
            browser_forward,
            browser_reload,
            browser_home,
            browser_layout,
            browser_current_url,
            browser_navigation_state
        ])
        .setup(|app| {
            app.manage(NavigationHistory::default());

            let window = tauri::Window::builder(app, "main")
                .title("Rudy Browser")
                .inner_size(1280.0, 820.0)
                .min_inner_size(720.0, 520.0)
                .center()
                .build()?;

            let scale_factor = window.scale_factor()?;
            let physical_size = window.inner_size()?;
            let logical_size = physical_size.to_logical::<f64>(scale_factor);

            // The local UI is created first. The browser content view is added
            // later so it overlays only the page area, never the toolbar/status.
            let ui_url = if cfg!(debug_assertions) {
                WebviewUrl::External("http://127.0.0.1:1420".parse()?)
            } else {
                WebviewUrl::App("index.html".into())
            };
            window.add_child(
                WebviewBuilder::new(MAIN_UI_LABEL, ui_url),
                LogicalPosition::new(0.0, 0.0),
                LogicalSize::new(logical_size.width, logical_size.height),
            )?;

            let download_directory = app
                .path()
                .download_dir()
                .unwrap_or_else(|_| std::env::temp_dir().join("Rudy Browser Downloads"));
            fs::create_dir_all(&download_directory)?;

            let event_handle = app.handle().clone();
            let navigation_handle = app.handle().clone();
            let title_handle = app.handle().clone();
            let title_window = window.clone();
            let download_handle = app.handle().clone();
            let download_dir = download_directory.clone();
            let popup_handle = app.handle().clone();

            let content_builder = WebviewBuilder::new(
                BROWSER_CONTENT_LABEL,
                WebviewUrl::External("about:blank".parse()?),
            )
            .on_navigation(is_allowed_browser_url)
            .on_new_window(move |url, _features| {
                let popup_url = url.as_str().to_string();
                if !matches!(url.scheme(), "http" | "https") || url.host_str().is_none() {
                    let _ = popup_handle.emit(
                        "browser-popup",
                        json!({
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
                    let navigation = content_webview(&main_thread_handle).and_then(|webview| {
                        webview.show().map_err(|error| error.to_string())?;
                        webview.navigate(url).map_err(|error| error.to_string())
                    });
                    let _ = main_thread_handle.emit(
                        "browser-popup",
                        json!({
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
                            "opened": false,
                            "url": popup_url,
                            "error": error.to_string()
                        }),
                    );
                }

                NewWindowResponse::Deny
            })
            .on_permission_request(|_webview, _kind| PermissionResponse::Deny)
            .on_page_load(move |webview, payload| {
                let phase = match payload.event() {
                    PageLoadEvent::Started => "started",
                    PageLoadEvent::Finished => "finished",
                };
                let _ = event_handle.emit(
                    "browser-load",
                    json!({
                        "event": phase,
                        "url": payload.url().as_str()
                    }),
                );
                match navigation_state(
                    &webview,
                    navigation_handle.state::<NavigationHistory>().inner(),
                ) {
                    Ok(state) => {
                        let _ = event_handle.emit("browser-navigation-state", state);
                    }
                    Err(error) => {
                        let _ = event_handle.emit("browser-navigation-state-error", error);
                    }
                }
            })
            .on_document_title_changed(move |_webview, title| {
                let trimmed = title.trim();
                let window_title = if trimmed.is_empty() {
                    "Rudy Browser".to_string()
                } else {
                    format!("{trimmed} — Rudy Browser")
                };
                let _ = title_window.set_title(&window_title);
                let _ = title_handle.emit("browser-title", title);
            })
            .on_download(move |_webview, event| {
                match event {
                    DownloadEvent::Requested { url, destination } => {
                        *destination = unique_download_path(&download_dir, &url);
                    }
                    DownloadEvent::Finished { url, path, success } => {
                        let _ = download_handle.emit(
                            "browser-download-finished",
                            json!({
                                "url": url.as_str(),
                                "path": path.map(|value| value.to_string_lossy().to_string()),
                                "success": success
                            }),
                        );
                    }
                    _ => {}
                }
                true
            });

            let content_view = window.add_child(
                content_builder,
                LogicalPosition::new(0.0, TOOLBAR_HEIGHT),
                LogicalSize::new(
                    logical_size.width,
                    (logical_size.height - TOOLBAR_HEIGHT - STATUS_HEIGHT).max(1.0),
                ),
            )?;
            content_view.hide()?;

            layout_browser_views(app.handle(), &window).map_err(std::io::Error::other)?;

            let layout_app = app.handle().clone();
            let layout_window = window.clone();
            window.on_window_event(move |event| {
                if matches!(
                    event,
                    WindowEvent::Resized(_) | WindowEvent::ScaleFactorChanged { .. }
                ) {
                    let app_handle = layout_app.clone();
                    let event_window = layout_window.clone();
                    let scheduling_handle = layout_app.clone();
                    if let Err(error) = scheduling_handle.run_on_main_thread(move || {
                        if let Err(layout_error) = layout_browser_views(&app_handle, &event_window)
                        {
                            let _ = app_handle.emit("browser-layout-error", layout_error);
                        }
                    }) {
                        let _ = layout_app.emit("browser-layout-error", error.to_string());
                    }
                }
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
    fn browser_layout_uses_logical_bounds_at_one_x_scale() {
        let layout = calculate_browser_layout(1280, 800, 1.0).unwrap();

        assert_eq!(layout.window_width, 1280.0);
        assert_eq!(layout.window_height, 800.0);
        assert_eq!(layout.content_top, TOOLBAR_HEIGHT);
        assert_eq!(
            layout.content_height,
            800.0 - TOOLBAR_HEIGHT - STATUS_HEIGHT
        );
    }

    #[test]
    fn browser_layout_converts_physical_bounds_for_high_dpi() {
        let layout = calculate_browser_layout(2560, 1600, 2.0).unwrap();

        assert_eq!(layout.window_width, 1280.0);
        assert_eq!(layout.window_height, 800.0);
        assert_eq!(layout.content_top, TOOLBAR_HEIGHT);
        assert_eq!(
            layout.content_height,
            800.0 - TOOLBAR_HEIGHT - STATUS_HEIGHT
        );
    }

    #[test]
    fn browser_layout_rejects_invalid_scale_factor() {
        assert!(calculate_browser_layout(1280, 800, 0.0).is_err());
        assert!(calculate_browser_layout(1280, 800, f64::NAN).is_err());
    }

    #[test]
    fn browser_layout_keeps_a_nonzero_content_extent_for_tiny_windows() {
        let layout = calculate_browser_layout(20, 20, 1.0).unwrap();

        assert!(layout.window_width >= 1.0);
        assert!(layout.window_height >= 1.0);
        assert!(layout.content_height >= 1.0);
        assert!(layout.content_top <= layout.window_height);
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
    fn download_filename_is_sanitized() {
        let url = Url::parse("https://example.com/path/report%20final.pdf").unwrap();
        assert_eq!(download_filename(&url), "report_20final.pdf");
    }

    #[test]
    fn empty_download_filename_uses_safe_fallback() {
        let url = Url::parse("https://example.com/").unwrap();
        assert_eq!(download_filename(&url), "download.bin");
    }
}
