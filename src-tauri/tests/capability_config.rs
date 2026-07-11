use serde_json::Value;
use std::{fs, path::PathBuf};

#[test]
fn local_backend_capability_is_narrowly_scoped() {
    let path = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("capabilities")
        .join("main.json");
    let content = fs::read_to_string(&path)
        .unwrap_or_else(|error| panic!("failed to read {}: {error}", path.display()));
    let capability: Value = serde_json::from_str(&content).expect("capability must be valid JSON");

    assert_eq!(capability["windows"], serde_json::json!(["main"]));
    assert_eq!(
        capability["remote"]["urls"],
        serde_json::json!(["http://127.0.0.1:*/*"])
    );

    assert_eq!(
        capability["permissions"],
        serde_json::json!([
            "allow-check-app-update",
            "allow-skip-app-update",
            "allow-download-app-update",
            "allow-restore-database"
        ])
    );
}
