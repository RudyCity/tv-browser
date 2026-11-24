fn main() {
    // Only the local toolbar webview is allowed to invoke these commands.
    // The remote browser-content webview is intentionally not granted access.
    tauri_build::try_build(tauri_build::Attributes::new().app_manifest(
        tauri_build::AppManifest::new().commands(&[
            "browser_navigate",
            "browser_back",
            "browser_forward",
            "browser_reload",
            "browser_home",
            "browser_layout",
            "browser_current_url",
            "browser_navigation_state",
        ]),
    ))
    .expect("failed to build Tauri application permissions");
}
