use std::{
    fs,
    path::Path,
    sync::{
        Arc, Mutex,
        atomic::{AtomicBool, Ordering},
    },
    time::{Duration, Instant, SystemTime},
};

use serde::{Deserialize, Serialize};
use tauri::Manager;
use tauri_plugin_updater::UpdaterExt;

use crate::BackendState;

const CONNECTING: &str = "CONNECTING";
const DOWNLOADING: &str = "DOWNLOADING";
const VERIFYING: &str = "VERIFYING";
const INSTALLING: &str = "INSTALLING";
const PROGRESS_EVENT: &str = "wm-update-progress";
const PROGRESS_INTERVAL: Duration = Duration::from_millis(200);
const STARTUP_FAILURE_COOLDOWN_SECONDS: u64 = 24 * 60 * 60;
static UPDATE_PREFERENCE_LOCK: Mutex<()> = Mutex::new(());

#[derive(Default)]
pub struct AppUpdateState {
    downloading: AtomicBool,
}

struct DownloadGuard<'a> {
    downloading: &'a AtomicBool,
}

impl Drop for DownloadGuard<'_> {
    fn drop(&mut self) {
        self.downloading.store(false, Ordering::Release);
    }
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdateProgress {
    phase: &'static str,
    downloaded_bytes: u64,
    total_bytes: Option<u64>,
    percent: Option<u8>,
    bytes_per_second: u64,
    eta_seconds: Option<u64>,
}

struct ProgressTracker {
    downloaded_bytes: u64,
    total_bytes: Option<u64>,
    started_at: Instant,
    last_emit_at: Option<Instant>,
}

fn calculate_progress(
    downloaded_bytes: u64,
    total_bytes: Option<u64>,
    elapsed: Duration,
) -> UpdateProgress {
    let elapsed_seconds = elapsed.as_secs_f64();
    let bytes_per_second = if elapsed_seconds > 0.0 {
        (downloaded_bytes as f64 / elapsed_seconds) as u64
    } else {
        0
    };
    let usable_total = total_bytes.filter(|total| *total > 0);
    let percent = usable_total.map(|total| {
        (((downloaded_bytes.min(total) as u128 * 100) / total as u128).min(100)) as u8
    });
    let eta_seconds = usable_total.and_then(|total| {
        if downloaded_bytes >= total {
            Some(0)
        } else if bytes_per_second > 0 {
            Some((total - downloaded_bytes).div_ceil(bytes_per_second))
        } else {
            None
        }
    });

    UpdateProgress {
        phase: DOWNLOADING,
        downloaded_bytes,
        total_bytes,
        percent,
        bytes_per_second,
        eta_seconds,
    }
}

fn phase_progress(
    phase: &'static str,
    downloaded_bytes: u64,
    total_bytes: Option<u64>,
    elapsed: Duration,
) -> UpdateProgress {
    let mut progress = calculate_progress(downloaded_bytes, total_bytes, elapsed);
    progress.phase = phase;
    progress
}

fn emit_progress(window: &tauri::WebviewWindow, progress: &UpdateProgress) {
    let Ok(detail) = serde_json::to_string(progress) else {
        return;
    };
    let script = format!(
        "window.dispatchEvent(new CustomEvent('{PROGRESS_EVENT}', {{ detail: {detail} }}));"
    );
    if let Err(error) = window.eval(&script) {
        crate::log_desktop(format!("update progress event failed: {error}"));
    }
}

fn log_progress(progress: &UpdateProgress) {
    crate::log_desktop(format!(
        "update phase={} downloadedBytes={} totalBytes={}",
        progress.phase,
        progress.downloaded_bytes,
        progress
            .total_bytes
            .map(|value| value.to_string())
            .unwrap_or_else(|| "unknown".to_string())
    ));
}

fn validate_expected_version(value: &str) -> Result<String, String> {
    let trimmed = value.trim();
    if trimmed.is_empty() || trimmed.chars().count() > 64 || value.chars().any(char::is_control) {
        return Err("版本号无效".to_string());
    }
    Ok(trimmed.to_string())
}

fn backend_port(window: &tauri::WebviewWindow) -> Result<u16, String> {
    let url = window.url().map_err(|error| error.to_string())?;
    if url.host_str() != Some("127.0.0.1") {
        return Err("无法确认当前后台服务地址".to_string());
    }
    url.port()
        .ok_or_else(|| "无法确认当前后台服务端口".to_string())
}

#[derive(Clone, Copy, Deserialize, PartialEq)]
#[serde(rename_all = "lowercase")]
pub enum UpdateCheckTrigger {
    Startup,
    Manual,
}

impl UpdateCheckTrigger {
    fn as_str(self) -> &'static str {
        match self {
            Self::Startup => "startup",
            Self::Manual => "manual",
        }
    }
}

#[derive(Serialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum UpdateCheckStatus {
    UpToDate,
    Available,
    Skipped,
    Deferred,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdateCheckResult {
    pub status: UpdateCheckStatus,
    pub current_version: String,
    pub target_version: Option<String>,
    pub notes: Option<String>,
    pub published_at: Option<String>,
    pub was_skipped: bool,
}

#[derive(Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct UpdatePreference {
    skipped_version: Option<String>,
    #[serde(default, deserialize_with = "deserialize_optional_u64_lenient")]
    startup_failure_retry_after_epoch_seconds: Option<u64>,
}

fn deserialize_optional_u64_lenient<'de, D>(deserializer: D) -> Result<Option<u64>, D::Error>
where
    D: serde::Deserializer<'de>,
{
    let value = serde_json::Value::deserialize(deserializer)?;
    Ok(value.as_u64())
}

fn current_epoch_seconds() -> u64 {
    SystemTime::now()
        .duration_since(SystemTime::UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs()
}

fn should_defer_startup_check(
    trigger: UpdateCheckTrigger,
    retry_after_epoch_seconds: Option<u64>,
    now_epoch_seconds: u64,
) -> bool {
    trigger == UpdateCheckTrigger::Startup
        && retry_after_epoch_seconds.is_some_and(|retry_after| now_epoch_seconds < retry_after)
}

fn should_suppress_startup(skipped_version: Option<&str>, target_version: &str) -> bool {
    skipped_version == Some(target_version)
}

fn should_suppress(
    trigger: UpdateCheckTrigger,
    skipped_version: Option<&str>,
    target_version: &str,
) -> bool {
    trigger == UpdateCheckTrigger::Startup
        && should_suppress_startup(skipped_version, target_version)
}

fn preference_path(app_data_dir: &Path) -> std::path::PathBuf {
    app_data_dir.join("update-preference.json")
}

fn read_preference_strict(app_data_dir: &Path) -> Result<UpdatePreference, String> {
    let path = preference_path(app_data_dir);
    let content = match fs::read(&path) {
        Ok(content) => content,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
            return Ok(UpdatePreference::default());
        }
        Err(error) => {
            return Err(format!(
                "failed to read update preference {}: {error}",
                path.display()
            ));
        }
    };
    serde_json::from_slice(&content).map_err(|error| {
        format!(
            "failed to parse update preference {}: {error}",
            path.display()
        )
    })
}

fn read_preference(app_data_dir: &Path) -> UpdatePreference {
    let result = UPDATE_PREFERENCE_LOCK
        .lock()
        .map_err(|error| format!("update preference lock poisoned: {error}"))
        .and_then(|_guard| read_preference_strict(app_data_dir));
    match result {
        Ok(preference) => preference,
        Err(error) => {
            crate::log_desktop(error);
            UpdatePreference::default()
        }
    }
}

fn write_preference_unlocked(
    app_data_dir: &Path,
    preference: &UpdatePreference,
) -> Result<(), String> {
    fs::create_dir_all(app_data_dir).map_err(|error| {
        format!(
            "failed to create update preference directory {}: {error}",
            app_data_dir.display()
        )
    })?;
    let path = preference_path(app_data_dir);
    let temp_path = app_data_dir.join("update-preference.json.tmp");
    let content = serde_json::to_vec_pretty(preference).map_err(|error| {
        format!(
            "failed to serialize update preference {}: {error}",
            path.display()
        )
    })?;
    fs::write(&temp_path, content).map_err(|error| {
        format!(
            "failed to write temporary update preference {}: {error}",
            temp_path.display()
        )
    })?;
    atomic_replace(&temp_path, &path).map_err(|error| {
        format!(
            "failed to replace update preference {}: {error}",
            path.display()
        )
    })
}

fn update_preference_transaction<F>(app_data_dir: &Path, update: F) -> Result<(), String>
where
    F: FnOnce(&mut UpdatePreference) -> bool,
{
    let _guard = UPDATE_PREFERENCE_LOCK
        .lock()
        .map_err(|error| format!("update preference lock poisoned: {error}"))?;
    let mut preference = read_preference_strict(app_data_dir)?;
    if !update(&mut preference) {
        return Ok(());
    }
    write_preference_unlocked(app_data_dir, &preference)
}

fn mark_startup_failure(app_data_dir: &Path, now_epoch_seconds: u64) -> Result<(), String> {
    update_preference_transaction(app_data_dir, |preference| {
        preference.startup_failure_retry_after_epoch_seconds =
            Some(now_epoch_seconds.saturating_add(STARTUP_FAILURE_COOLDOWN_SECONDS));
        true
    })
}

fn mark_startup_failure_now(app_data_dir: &Path) -> Result<(), String> {
    mark_startup_failure(app_data_dir, current_epoch_seconds())
}

fn clear_startup_failure(app_data_dir: &Path) -> Result<(), String> {
    update_preference_transaction(app_data_dir, |preference| {
        preference
            .startup_failure_retry_after_epoch_seconds
            .take()
            .is_some()
    })
}

fn set_skipped_version(preference: &mut UpdatePreference, version: &str) {
    preference.skipped_version = Some(version.to_string());
}

fn persist_skipped_version(app_data_dir: &Path, version: &str) -> Result<(), String> {
    update_preference_transaction(app_data_dir, |preference| {
        set_skipped_version(preference, version);
        true
    })
}

#[cfg(not(windows))]
fn atomic_replace(source: &Path, destination: &Path) -> std::io::Result<()> {
    fs::rename(source, destination)
}

#[cfg(windows)]
fn atomic_replace(source: &Path, destination: &Path) -> std::io::Result<()> {
    use std::os::windows::ffi::OsStrExt;

    const MOVEFILE_REPLACE_EXISTING: u32 = 0x1;
    const MOVEFILE_WRITE_THROUGH: u32 = 0x8;

    #[link(name = "kernel32")]
    unsafe extern "system" {
        fn MoveFileExW(existing: *const u16, new: *const u16, flags: u32) -> i32;
    }

    let source = source
        .as_os_str()
        .encode_wide()
        .chain(std::iter::once(0))
        .collect::<Vec<_>>();
    let destination = destination
        .as_os_str()
        .encode_wide()
        .chain(std::iter::once(0))
        .collect::<Vec<_>>();
    let moved = unsafe {
        MoveFileExW(
            source.as_ptr(),
            destination.as_ptr(),
            MOVEFILE_REPLACE_EXISTING | MOVEFILE_WRITE_THROUGH,
        )
    };
    if moved == 0 {
        Err(std::io::Error::last_os_error())
    } else {
        Ok(())
    }
}

#[tauri::command]
pub async fn check_app_update(
    trigger: UpdateCheckTrigger,
    app: tauri::AppHandle,
) -> Result<UpdateCheckResult, String> {
    let current_version = app.package_info().version.to_string();
    crate::log_desktop(format!(
        "update check trigger={} current={current_version}",
        trigger.as_str()
    ));
    let app_data_dir = app
        .path()
        .app_data_dir()
        .map_err(|error| error.to_string())?;
    let preference = read_preference(&app_data_dir);
    let now_epoch_seconds = current_epoch_seconds();
    if should_defer_startup_check(
        trigger,
        preference.startup_failure_retry_after_epoch_seconds,
        now_epoch_seconds,
    ) {
        crate::log_desktop("startup update check deferred by cooldown".to_string());
        return Ok(UpdateCheckResult {
            status: UpdateCheckStatus::Deferred,
            current_version,
            target_version: None,
            notes: None,
            published_at: None,
            was_skipped: false,
        });
    }
    let updater = match app.updater() {
        Ok(updater) => updater,
        Err(error) => {
            if trigger == UpdateCheckTrigger::Startup {
                if let Err(preference_error) = mark_startup_failure_now(&app_data_dir) {
                    crate::log_desktop(format!(
                        "failed to persist startup update cooldown: {preference_error}"
                    ));
                }
            }
            crate::log_desktop(format!("update check error: {error}"));
            return Err(error.to_string());
        }
    };
    let update = match updater.check().await {
        Ok(update) => update,
        Err(error) => {
            if trigger == UpdateCheckTrigger::Startup {
                if let Err(preference_error) = mark_startup_failure_now(&app_data_dir) {
                    crate::log_desktop(format!(
                        "failed to persist startup update cooldown: {preference_error}"
                    ));
                }
            }
            crate::log_desktop(format!("update check error: {error}"));
            return Err(error.to_string());
        }
    };
    if let Err(preference_error) = clear_startup_failure(&app_data_dir) {
        crate::log_desktop(format!(
            "failed to clear startup update cooldown: {preference_error}"
        ));
    }

    let Some(update) = update else {
        crate::log_desktop(format!(
            "update check trigger={} current={current_version} target=none",
            trigger.as_str()
        ));
        return Ok(UpdateCheckResult {
            status: UpdateCheckStatus::UpToDate,
            current_version,
            target_version: None,
            notes: None,
            published_at: None,
            was_skipped: false,
        });
    };

    let was_skipped =
        should_suppress_startup(preference.skipped_version.as_deref(), &update.version);
    let status = if should_suppress(
        trigger,
        preference.skipped_version.as_deref(),
        &update.version,
    ) {
        UpdateCheckStatus::Skipped
    } else {
        UpdateCheckStatus::Available
    };
    crate::log_desktop(format!(
        "update check trigger={} current={} target={}",
        trigger.as_str(),
        update.current_version,
        update.version
    ));

    Ok(UpdateCheckResult {
        status,
        current_version: update.current_version,
        target_version: Some(update.version),
        notes: update.body,
        published_at: update.date.map(|date| date.to_string()),
        was_skipped,
    })
}

#[tauri::command]
pub fn skip_app_update(version: String, app: tauri::AppHandle) -> Result<(), String> {
    let version = version.trim();
    if version.is_empty() || version.chars().count() > 64 {
        return Err("版本号不能为空且不能超过 64 个字符".to_string());
    }
    let app_data_dir = app
        .path()
        .app_data_dir()
        .map_err(|error| error.to_string())?;
    persist_skipped_version(&app_data_dir, version).map_err(|error| {
        crate::log_desktop(format!("failed to persist skipped update version: {error}"));
        error
    })
}

#[tauri::command]
pub async fn download_app_update(
    expected_version: String,
    app: tauri::AppHandle,
    window: tauri::WebviewWindow,
    update_state: tauri::State<'_, AppUpdateState>,
    backend_state: tauri::State<'_, BackendState>,
) -> Result<(), String> {
    let expected_version = validate_expected_version(&expected_version)?;
    if update_state.downloading.swap(true, Ordering::AcqRel) {
        return Err("更新正在下载，请勿重复操作".to_string());
    }
    let _download_guard = DownloadGuard {
        downloading: &update_state.downloading,
    };

    let current_version = app.package_info().version.to_string();
    crate::log_desktop(format!(
        "update download trigger=confirmed current={current_version} target={expected_version} phase={CONNECTING}"
    ));
    let connecting = phase_progress(CONNECTING, 0, None, Duration::ZERO);
    emit_progress(&window, &connecting);
    log_progress(&connecting);

    let updater = app.updater().map_err(|error| {
        crate::log_desktop(format!(
            "update download target={expected_version} phase={CONNECTING} error={error}"
        ));
        error.to_string()
    })?;
    let update = updater.check().await.map_err(|error| {
        crate::log_desktop(format!(
            "update download target={expected_version} phase={CONNECTING} error={error}"
        ));
        error.to_string()
    })?;
    let Some(update) = update.filter(|update| update.version == expected_version) else {
        crate::log_desktop(format!(
            "update download current={current_version} target={expected_version} error=version changed"
        ));
        return Err("版本信息已变化，请重新确认".to_string());
    };

    let tracker = Arc::new(Mutex::new(ProgressTracker {
        downloaded_bytes: 0,
        total_bytes: None,
        started_at: Instant::now(),
        last_emit_at: None,
    }));
    let chunk_tracker = Arc::clone(&tracker);
    let chunk_window = window.clone();
    let finish_tracker = Arc::clone(&tracker);
    let finish_window = window.clone();
    let verifying = Arc::new(AtomicBool::new(false));
    let finish_verifying = Arc::clone(&verifying);
    let bytes = update
        .download(
            move |chunk_length, content_length| {
                let Ok(mut tracker) = chunk_tracker.lock() else {
                    return;
                };
                tracker.downloaded_bytes =
                    tracker.downloaded_bytes.saturating_add(chunk_length as u64);
                tracker.total_bytes = content_length;
                let now = Instant::now();
                if tracker
                    .last_emit_at
                    .is_some_and(|last_emit| now.duration_since(last_emit) < PROGRESS_INTERVAL)
                {
                    return;
                }
                tracker.last_emit_at = Some(now);
                let progress = calculate_progress(
                    tracker.downloaded_bytes,
                    tracker.total_bytes,
                    now.duration_since(tracker.started_at),
                );
                emit_progress(&chunk_window, &progress);
                log_progress(&progress);
            },
            move || {
                finish_verifying.store(true, Ordering::Release);
                let Ok(tracker) = finish_tracker.lock() else {
                    return;
                };
                let progress = phase_progress(
                    VERIFYING,
                    tracker.downloaded_bytes,
                    tracker.total_bytes,
                    tracker.started_at.elapsed(),
                );
                emit_progress(&finish_window, &progress);
                log_progress(&progress);
            },
        )
        .await
        .map_err(|error| {
            let phase = if verifying.load(Ordering::Acquire) {
                VERIFYING
            } else {
                DOWNLOADING
            };
            crate::log_desktop(format!(
                "update download target={expected_version} phase={phase} error={error}"
            ));
            error.to_string()
        })?;

    let (downloaded_bytes, total_bytes, elapsed) = tracker
        .lock()
        .map(|tracker| {
            (
                tracker.downloaded_bytes,
                tracker.total_bytes,
                tracker.started_at.elapsed(),
            )
        })
        .unwrap_or((bytes.len() as u64, Some(bytes.len() as u64), Duration::ZERO));
    let installing = phase_progress(INSTALLING, downloaded_bytes, total_bytes, elapsed);
    emit_progress(&window, &installing);
    log_progress(&installing);

    let original_port = backend_port(&window)?;
    crate::stop_backend_checked(&backend_state).map_err(|error| {
        crate::log_desktop(format!(
            "update install target={expected_version} phase={INSTALLING} error={error}"
        ));
        format!("无法安全停止原版本后台，已取消安装: {error}")
    })?;
    if let Err(install_error) = update.install(bytes) {
        crate::log_desktop(format!(
            "update install target={expected_version} phase={INSTALLING} error={install_error}"
        ));
        let candidates = crate::path_candidates(app.path().resource_dir().ok());
        let restart_result = crate::start_backend_on_port(original_port, &candidates)
            .map_err(|error| error.to_string())
            .and_then(|child| backend_state.replace_child(child));
        return match restart_result {
            Ok(()) => Err(format!(
                "安装更新失败: {install_error}；原版本后台已重新启动"
            )),
            Err(restart_error) => {
                crate::log_desktop(format!(
                    "update rollback target={expected_version} error={restart_error}"
                ));
                Err(format!(
                    "安装更新失败: {install_error}；原版本后台重启失败: {restart_error}"
                ))
            }
        };
    }

    crate::log_desktop(format!(
        "update install target={expected_version} phase={INSTALLING} completed"
    ));
    app.restart();
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::{
        fs,
        path::PathBuf,
        sync::{Arc, Barrier},
        thread,
        time::{Duration, SystemTime},
    };

    fn fixture_root(name: &str) -> PathBuf {
        let unique = SystemTime::now()
            .duration_since(SystemTime::UNIX_EPOCH)
            .unwrap()
            .as_nanos();
        let root = std::env::temp_dir().join(format!("wm-{name}-{}-{unique}", std::process::id()));
        fs::create_dir_all(&root).unwrap();
        root
    }

    #[test]
    fn startup_check_suppresses_only_matching_skipped_version() {
        assert!(should_suppress_startup(Some("1.7.7"), "1.7.7"));
        assert!(!should_suppress_startup(Some("1.7.7"), "1.7.8"));
        assert!(!should_suppress_startup(None, "1.7.7"));
    }

    #[test]
    fn manual_check_never_suppresses_skipped_version() {
        assert!(!should_suppress(
            UpdateCheckTrigger::Manual,
            Some("1.7.7"),
            "1.7.7"
        ));
    }

    #[test]
    fn preference_round_trip_replaces_previous_version() {
        let root = fixture_root("update-preference");
        update_preference_transaction(&root, |preference| {
            *preference = UpdatePreference {
                skipped_version: Some("1.7.7".to_string()),
                startup_failure_retry_after_epoch_seconds: None,
            };
            true
        })
        .unwrap();
        update_preference_transaction(&root, |preference| {
            *preference = UpdatePreference {
                skipped_version: Some("1.7.8".to_string()),
                startup_failure_retry_after_epoch_seconds: Some(123_456),
            };
            true
        })
        .unwrap();
        let preference = read_preference(&root);
        assert_eq!(preference.skipped_version.as_deref(), Some("1.7.8"));
        assert_eq!(
            preference.startup_failure_retry_after_epoch_seconds,
            Some(123_456)
        );
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn legacy_preference_without_cooldown_is_readable() {
        let root = fixture_root("legacy-update-preference");
        fs::write(preference_path(&root), r#"{"skippedVersion":"1.7.7"}"#).unwrap();

        let preference = read_preference(&root);

        assert_eq!(preference.skipped_version.as_deref(), Some("1.7.7"));
        assert_eq!(preference.startup_failure_retry_after_epoch_seconds, None);
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn invalid_startup_failure_cooldown_does_not_discard_skipped_version() {
        for (name, cooldown) in [
            ("string", r#""invalid""#),
            ("negative", "-1"),
            ("null", "null"),
            ("above-u64-max", "18446744073709551616"),
            ("overflowing-exponent", "1e400"),
        ] {
            let root = fixture_root(&format!("invalid-update-cooldown-{name}"));
            fs::write(
                preference_path(&root),
                format!(
                    r#"{{"skippedVersion":"1.7.8","startupFailureRetryAfterEpochSeconds":{cooldown}}}"#
                ),
            )
            .unwrap();

            let preference = read_preference(&root);

            assert_eq!(preference.skipped_version.as_deref(), Some("1.7.8"));
            assert_eq!(preference.startup_failure_retry_after_epoch_seconds, None);
            fs::remove_dir_all(root).unwrap();
        }
    }

    #[test]
    fn startup_check_defers_only_before_retry_deadline() {
        assert!(should_defer_startup_check(
            UpdateCheckTrigger::Startup,
            Some(1_000),
            999
        ));
        assert!(!should_defer_startup_check(
            UpdateCheckTrigger::Startup,
            Some(1_000),
            1_000
        ));
        assert!(!should_defer_startup_check(
            UpdateCheckTrigger::Startup,
            None,
            999
        ));
    }

    #[test]
    fn manual_check_never_defers_for_startup_failure() {
        assert!(!should_defer_startup_check(
            UpdateCheckTrigger::Manual,
            Some(u64::MAX),
            0
        ));
    }

    #[test]
    fn marking_and_clearing_startup_failure_preserves_skipped_version() {
        let root = fixture_root("update-cooldown");
        persist_skipped_version(&root, "1.7.7").unwrap();

        mark_startup_failure(&root, 1_000).unwrap();
        let marked = read_preference(&root);
        assert_eq!(marked.skipped_version.as_deref(), Some("1.7.7"));
        assert_eq!(
            marked.startup_failure_retry_after_epoch_seconds,
            Some(1_000 + STARTUP_FAILURE_COOLDOWN_SECONDS)
        );

        clear_startup_failure(&root).unwrap();
        let cleared = read_preference(&root);
        assert_eq!(cleared.skipped_version.as_deref(), Some("1.7.7"));
        assert_eq!(cleared.startup_failure_retry_after_epoch_seconds, None);
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn setting_skipped_version_preserves_startup_failure_cooldown() {
        let mut preference = UpdatePreference {
            skipped_version: Some("1.7.7".to_string()),
            startup_failure_retry_after_epoch_seconds: Some(123_456),
        };

        set_skipped_version(&mut preference, "1.7.8");

        assert_eq!(preference.skipped_version.as_deref(), Some("1.7.8"));
        assert_eq!(
            preference.startup_failure_retry_after_epoch_seconds,
            Some(123_456)
        );
    }

    #[test]
    fn clearing_corrupt_preference_returns_error_without_overwriting_file() {
        let root = fixture_root("corrupt-update-preference");
        let path = preference_path(&root);
        let original = b"{not-valid-json";
        fs::write(&path, original).unwrap();

        assert!(clear_startup_failure(&root).is_err());
        assert_eq!(fs::read(&path).unwrap(), original);
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn concurrent_skip_and_failure_mark_preserve_both_fields() {
        let root = fixture_root("concurrent-update-preference");

        for round in 0..32 {
            let round_root = root.join(round.to_string());
            fs::create_dir_all(&round_root).unwrap();
            let barrier = Arc::new(Barrier::new(2));
            let skip_root = round_root.clone();
            let skip_barrier = Arc::clone(&barrier);
            let skip = thread::spawn(move || {
                skip_barrier.wait();
                persist_skipped_version(&skip_root, "1.7.7")
            });
            let mark_root = round_root.clone();
            let mark_barrier = Arc::clone(&barrier);
            let mark = thread::spawn(move || {
                mark_barrier.wait();
                mark_startup_failure(&mark_root, 1_000 + round)
            });

            skip.join().unwrap().unwrap();
            mark.join().unwrap().unwrap();
            let preference = read_preference(&round_root);
            assert_eq!(preference.skipped_version.as_deref(), Some("1.7.7"));
            assert_eq!(
                preference.startup_failure_retry_after_epoch_seconds,
                Some(1_000 + round + STARTUP_FAILURE_COOLDOWN_SECONDS)
            );
        }

        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn clearing_absent_cooldown_does_not_create_or_rewrite_preference() {
        let empty_root = fixture_root("clear-empty-update-preference");
        clear_startup_failure(&empty_root).unwrap();
        assert!(!preference_path(&empty_root).exists());

        let existing_root = fixture_root("clear-existing-update-preference");
        let path = preference_path(&existing_root);
        let original = br#"{ "skippedVersion": "1.7.7" }"#;
        fs::write(&path, original).unwrap();
        clear_startup_failure(&existing_root).unwrap();
        assert_eq!(fs::read(&path).unwrap(), original);

        fs::remove_dir_all(empty_root).unwrap();
        fs::remove_dir_all(existing_root).unwrap();
    }

    #[test]
    fn deferred_status_serializes_as_screaming_snake_case() {
        assert_eq!(
            serde_json::to_string(&UpdateCheckStatus::Deferred).unwrap(),
            r#""DEFERRED""#
        );
    }

    #[test]
    fn progress_includes_percent_speed_and_eta_when_total_is_known() {
        let value = calculate_progress(50, Some(100), Duration::from_secs(10));
        assert_eq!(value.percent, Some(50));
        assert_eq!(value.bytes_per_second, 5);
        assert_eq!(value.eta_seconds, Some(10));
    }

    #[test]
    fn progress_does_not_invent_percent_without_content_length() {
        let value = calculate_progress(50, None, Duration::from_secs(10));
        assert_eq!(value.percent, None);
        assert_eq!(value.eta_seconds, None);
    }

    #[test]
    fn expected_version_is_trimmed_before_use() {
        assert_eq!(validate_expected_version(" 1.7.7 ").unwrap(), "1.7.7");
    }

    #[test]
    fn expected_version_rejects_empty_or_oversized_values() {
        assert!(validate_expected_version("  ").is_err());
        assert!(validate_expected_version(&"1".repeat(65)).is_err());
    }

    #[test]
    fn expected_version_rejects_control_characters() {
        assert!(validate_expected_version("\n1.7.7").is_err());
        assert!(validate_expected_version("1.7.7\nforged-log").is_err());
        assert!(validate_expected_version("1.7.7\0").is_err());
    }
}
