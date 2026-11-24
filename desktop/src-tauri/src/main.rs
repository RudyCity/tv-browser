use std::{
    fs,
    path::{Path, PathBuf},
};

use serde_json::json;
use tauri::{
    webview::{
        DownloadEvent, NewWindowResponse, PageLoadEvent, PermissionResponse, WebviewBuilder,
    },
    Emitter, LogicalPosition, LogicalSize, Manager, WebviewUrl, Window,
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

#[tauri::command]
fn browser_back(app: tauri::AppHandle) -> Result<(), String> {
    content_webview(&app)?
        .eval("history.back();")
        .map_err(|error| error.to_string())
}

#[tauri::command]
fn browser_forward(app: tauri::AppHandle) -> Result<(), String> {
    content_webview(&app)?
        .eval("history.forward();")
        .map_err(|error| error.to_string())
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
    let webview = content_webview(&app)?;
    let scale_factor = window.scale_factor().map_err(|error| error.to_string())?;
    let physical_size = window.inner_size().map_err(|error| error.to_string())?;
    let logical_size = physical_size.to_logical::<f64>(scale_factor);
    let content_height = (logical_size.height - TOOLBAR_HEIGHT - STATUS_HEIGHT).max(0.0);

    webview
        .set_position(LogicalPosition::new(0.0, TOOLBAR_HEIGHT))
        .map_err(|error| error.to_string())?;
    webview
        .set_size(LogicalSize::new(logical_size.width, content_height))
        .map_err(|error| error.to_string())
}

fn download_filename(url: &Url) -> String {
    let raw_name = url
        .path_segments()
        .and_then(|segments| segments.filter(|part| !part.is_empty()).last())
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
    let stem = path.file_stem().and_then(|value| value.to_str()).unwrap_or("download");
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
            browser_current_url
        ])
        .setup(|app| {
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
            let title_handle = app.handle().clone();
            let title_window = window.clone();
            let download_handle = app.handle().clone();
            let download_dir = download_directory.clone();

            let content_builder = WebviewBuilder::new(
                BROWSER_CONTENT_LABEL,
                WebviewUrl::External("about:blank".parse()?),
            )
            .on_navigation(is_allowed_browser_url)
            .on_new_window(|_url, _features| NewWindowResponse::Deny)
            .on_permission_request(|_webview, _kind| PermissionResponse::Deny)
            .on_page_load(move |_webview, payload| {
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
                    DownloadEvent::Finished {
                        url,
                        path,
                        success,
                    } => {
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
                    (logical_size.height - TOOLBAR_HEIGHT - STATUS_HEIGHT).max(0.0),
                ),
            )?;
            content_view.hide()?;

            Ok(())
        })
        .run(tauri::generate_context!())
        .expect("error while running Rudy Browser");
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn only_http_https_and_blank_are_navigable() {
        assert!(is_allowed_browser_url(&Url::parse("https://example.com").unwrap()));
        assert!(is_allowed_browser_url(&Url::parse("http://localhost:3000").unwrap()));
        assert!(is_allowed_browser_url(&Url::parse("about:blank").unwrap()));
        assert!(!is_allowed_browser_url(&Url::parse("file:///etc/passwd").unwrap()));
        assert!(!is_allowed_browser_url(&Url::parse("javascript:alert(1)").unwrap()));
        assert!(!is_allowed_browser_url(&Url::parse("ftp://example.com").unwrap()));
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
