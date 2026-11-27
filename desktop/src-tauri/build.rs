fn main() {
    // Only the local toolbar webview is allowed to invoke these commands.
    // Remote page webviews are intentionally not granted access.
    tauri_build::try_build(tauri_build::Attributes::new().app_manifest(
        tauri_build::AppManifest::new().commands(&[
            "browser_layout",
            "browser_tab_list",
            "browser_tab_new",
            "browser_tab_activate",
            "browser_tab_close",
            "browser_tab_move",
            "browser_navigate",
            "browser_back",
            "browser_forward",
            "browser_reload",
            "browser_home",
            "browser_navigation_state",
            "browser_set_zoom",
            "browser_get_zoom",
            "browser_find",
            "browser_permissions_set",
            "browser_clear_site_data",
            "browser_open_path",
            "browser_toggle_fullscreen",
        ]),
    ))
    .expect("failed to build Tauri application permissions");
}
