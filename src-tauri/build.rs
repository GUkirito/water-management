fn main() {
    let attributes =
        tauri_build::Attributes::new().app_manifest(tauri_build::AppManifest::new().commands(&[
            "check_app_update",
            "skip_app_update",
            "download_app_update",
            "restore_database",
        ]));
    tauri_build::try_build(attributes).expect("failed to build Tauri application");
}
