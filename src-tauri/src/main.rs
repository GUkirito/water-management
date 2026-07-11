#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod app_update;

use std::{
    fs,
    io::{Read, Write},
    net::{SocketAddr, TcpListener, TcpStream},
    path::{Path, PathBuf},
    process::{Child, Command, Stdio},
    sync::Mutex,
    thread,
    time::{Duration, Instant, SystemTime},
};

#[cfg(windows)]
use std::os::windows::process::CommandExt;

use serde::{Deserialize, Serialize};
use tauri::{
    Manager, WebviewUrl, WebviewWindowBuilder, WindowEvent,
    menu::{MenuBuilder, MenuItemBuilder},
    tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent},
};
use tauri_plugin_global_shortcut::{Code, GlobalShortcutExt, Modifiers, Shortcut, ShortcutState};

type AppResult<T> = Result<T, Box<dyn std::error::Error>>;

#[cfg(windows)]
const CREATE_NO_WINDOW: u32 = 0x08000000;

pub(crate) struct BackendState(Mutex<Option<Child>>);

impl BackendState {
    pub(crate) fn replace_child(&self, child: Child) -> Result<(), String> {
        *self.0.lock().map_err(|_| "后端状态锁已损坏".to_string())? = Some(child);
        Ok(())
    }
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct RestoreResult {
    status: String,
    message: String,
    port: u16,
}

struct RestoreFiles {
    descriptor: PathBuf,
    staged: PathBuf,
    rollback: PathBuf,
}

impl Drop for BackendState {
    fn drop(&mut self) {
        stop_backend(self);
    }
}

#[derive(Serialize, Deserialize)]
struct WindowState {
    x: i32,
    y: i32,
    width: f64,
    height: f64,
    maximized: bool,
}

fn main() {
    log_desktop("desktop process starting");
    tauri::Builder::default()
        .plugin(tauri_plugin_single_instance::init(|app, _args, _cwd| {
            if let Some(window) = app.get_webview_window("main") {
                let _ = window.show();
                let _ = window.unminimize();
                let _ = window.set_focus();
            }
        }))
        .plugin(tauri_plugin_global_shortcut::Builder::new().build())
        .plugin(tauri_plugin_updater::Builder::new().build())
        .manage(BackendState(Mutex::new(None)))
        .manage(app_update::AppUpdateState::default())
        .invoke_handler(tauri::generate_handler![
            app_update::check_app_update,
            app_update::skip_app_update,
            app_update::download_app_update,
            restore_database
        ])
        .setup(|app| {
            let candidates = path_candidates(app.path().resource_dir().ok());
            log_desktop(format!(
                "resource candidates: {}",
                format_paths(&candidates)
            ));
            let (port, child) = start_backend_with_retry(&candidates)?;
            log_desktop(format!("backend ready on port {port}"));

            app.state::<BackendState>()
                .replace_child(child)
                .map_err(std::io::Error::other)?;

            let url = format!("http://127.0.0.1:{port}/");
            let saved_state = read_window_state(app);
            let mut builder =
                WebviewWindowBuilder::new(app, "main", WebviewUrl::External(url.parse()?))
                    .title("村级自来水管理系统")
                    .resizable(true);
            if let Some(state) = &saved_state {
                builder = builder
                    .inner_size(state.width, state.height)
                    .position(state.x as f64, state.y as f64);
            } else {
                builder = builder.inner_size(1280.0, 820.0);
            }
            let window = builder.build()?;
            if saved_state.as_ref().is_some_and(|state| state.maximized) {
                let _ = window.maximize();
            }

            setup_tray(app)?;
            setup_shortcuts(app)?;

            let health_window = window.clone();
            thread::spawn(move || match check_data_integrity(port) {
                Ok(Some(issues)) => notify_data_integrity_issues(&health_window, &issues),
                Ok(None) => {}
                Err(error) => eprintln!("健康检查失败: {error}"),
            });

            Ok(())
        })
        .on_window_event(|window, event| {
            if let WindowEvent::CloseRequested { api, .. } = event {
                api.prevent_close();
                save_window_state(window);
                let _ = window.hide();
            }
        })
        .run(tauri::generate_context!())
        .unwrap_or_else(|error| {
            log_desktop(format!("desktop app failed: {error}"));
            std::process::exit(1);
        });
}

fn setup_tray(app: &tauri::App) -> tauri::Result<()> {
    let show = MenuItemBuilder::with_id("show", "显示主窗口").build(app)?;
    let quit = MenuItemBuilder::with_id("quit", "退出").build(app)?;
    let menu = MenuBuilder::new(app).items(&[&show, &quit]).build()?;

    let mut tray = TrayIconBuilder::new()
        .menu(&menu)
        .show_menu_on_left_click(false)
        .tooltip("村级自来水管理系统")
        .on_menu_event(|app, event| match event.id().as_ref() {
            "show" => show_main_window(app),
            "quit" => quit_app(app),
            _ => {}
        })
        .on_tray_icon_event(|tray, event| {
            if let TrayIconEvent::Click {
                button: MouseButton::Left,
                button_state: MouseButtonState::Up,
                ..
            } = event
            {
                toggle_main_window(tray.app_handle());
            }
        });

    if let Some(icon) = app.default_window_icon().cloned() {
        tray = tray.icon(icon);
    }

    tray.build(app)?;
    Ok(())
}

fn setup_shortcuts(app: &tauri::App) -> AppResult<()> {
    let shortcut = Shortcut::new(Some(Modifiers::CONTROL | Modifiers::SHIFT), Code::KeyF);
    app.global_shortcut()
        .on_shortcut(shortcut, |app, _shortcut, event| {
            if event.state == ShortcutState::Pressed {
                show_main_window(app);
            }
        })?;
    Ok(())
}

fn toggle_main_window(app: &tauri::AppHandle) {
    let Some(window) = app.get_webview_window("main") else {
        return;
    };

    if window.is_visible().unwrap_or(false) {
        let _ = window.hide();
    } else {
        let _ = window.show();
        let _ = window.unminimize();
        let _ = window.set_focus();
    }
}

fn show_main_window(app: &tauri::AppHandle) {
    if let Some(window) = app.get_webview_window("main") {
        let _ = window.show();
        let _ = window.unminimize();
        let _ = window.set_focus();
    }
}

fn quit_app(app: &tauri::AppHandle) {
    let state = app.state::<BackendState>();
    stop_backend(&state);
    app.exit(0);
}

#[tauri::command]
fn restore_database(
    token: String,
    app: tauri::AppHandle,
    state: tauri::State<'_, BackendState>,
) -> Result<RestoreResult, String> {
    let home = user_home().ok_or_else(|| "无法确定用户数据目录".to_string())?;
    let staging_root = home.join(".water-management").join("data").join("restore-staging");
    let data_root = home.join(".water-management").join("data");
    let database = data_root.join("water_meter.db");
    let files = resolve_restore_files(&staging_root, &token).map_err(|error| error.to_string())?;
    let previous = staging_root.join(format!("{token}.previous.db"));
    let candidates = path_candidates(app.path().resource_dir().ok());

    stop_backend(&state);
    if let Err(error) = replace_database_file(&database, &files.staged, &previous) {
        return rollback_after_restore_failure(
            &state,
            &candidates,
            &database,
            &files.rollback,
            format!("替换数据库失败: {error}"),
        );
    }

    match start_backend_with_retry(&candidates) {
        Ok((port, child)) => {
            state.replace_child(child)?;
            if !accounting_health_endpoint_available(port) {
                stop_backend(&state);
                return rollback_after_restore_failure(
                    &state,
                    &candidates,
                    &database,
                    &files.rollback,
                    "恢复后的账务健康检查接口不可用".to_string(),
                );
            }

            let retained_rollback = data_root.join(format!("rollback_before_restore_{token}.db"));
            if retained_rollback.exists() {
                let _ = fs::remove_file(&retained_rollback);
            }
            fs::rename(&files.rollback, &retained_rollback)
                .map_err(|error| format!("保留恢复前回滚备份失败: {error}"))?;
            let _ = fs::remove_file(previous);
            let _ = fs::remove_file(files.descriptor);
            Ok(RestoreResult {
                status: "COMPLETED".to_string(),
                message: "数据库恢复完成，后端已重启并通过健康检查".to_string(),
                port,
            })
        }
        Err(error) => rollback_after_restore_failure(
            &state,
            &candidates,
            &database,
            &files.rollback,
            format!("恢复后端启动失败: {error}"),
        ),
    }
}

fn rollback_after_restore_failure(
    state: &BackendState,
    candidates: &[PathBuf],
    database: &Path,
    rollback: &Path,
    reason: String,
) -> Result<RestoreResult, String> {
    stop_backend(state);
    restore_rollback_file(database, rollback)
        .map_err(|error| format!("{reason}；自动回滚失败: {error}"))?;
    let (port, child) = start_backend_with_retry(candidates)
        .map_err(|error| format!("{reason}；数据库已回滚，但后端重启失败: {error}"))?;
    state.replace_child(child)?;
    Ok(RestoreResult {
        status: "ROLLED_BACK".to_string(),
        message: format!("{reason}；系统已自动恢复原数据库"),
        port,
    })
}

fn resolve_restore_files(staging_root: &Path, token: &str) -> AppResult<RestoreFiles> {
    if token.len() != 36
        || !token
            .bytes()
            .all(|value| value.is_ascii_hexdigit() || value == b'-')
    {
        return Err("无效的数据库恢复令牌".into());
    }
    let root = staging_root.to_path_buf();
    let descriptor = root.join(format!("{token}.properties"));
    if !descriptor.starts_with(&root) || !descriptor.is_file() {
        return Err("数据库恢复令牌不存在".into());
    }
    let content = fs::read_to_string(&descriptor)?;
    let staged_name = descriptor_value(&content, "staged")?;
    let rollback_name = descriptor_value(&content, "rollback")?;
    let staged = safe_descriptor_file(&root, staged_name)?;
    let rollback = safe_descriptor_file(&root, rollback_name)?;
    if !staged.is_file() || !rollback.is_file() {
        return Err("数据库恢复暂存文件不完整".into());
    }
    Ok(RestoreFiles {
        descriptor,
        staged,
        rollback,
    })
}

fn descriptor_value<'a>(content: &'a str, key: &str) -> AppResult<&'a str> {
    content
        .lines()
        .find_map(|line| line.strip_prefix(&format!("{key}=")))
        .filter(|value| !value.trim().is_empty())
        .map(str::trim)
        .ok_or_else(|| format!("数据库恢复描述缺少 {key}").into())
}

fn safe_descriptor_file(root: &Path, name: &str) -> AppResult<PathBuf> {
    let name_path = Path::new(name);
    if name_path.file_name().and_then(|value| value.to_str()) != Some(name) {
        return Err("数据库恢复描述包含非法路径".into());
    }
    let path = root.join(name);
    if !path.starts_with(root) {
        return Err("数据库恢复路径超出允许目录".into());
    }
    Ok(path)
}

fn replace_database_file(database: &Path, staged: &Path, previous: &Path) -> AppResult<()> {
    let parent = database.parent().ok_or("数据库目录无效")?;
    fs::create_dir_all(parent)?;
    remove_database_sidecars(database);
    if previous.exists() {
        fs::remove_file(previous)?;
    }
    if database.exists() {
        fs::rename(database, previous)?;
    }
    if let Err(error) = fs::rename(staged, database) {
        if previous.exists() && !database.exists() {
            let _ = fs::rename(previous, database);
        }
        return Err(error.into());
    }
    Ok(())
}

fn restore_rollback_file(database: &Path, rollback: &Path) -> AppResult<()> {
    remove_database_sidecars(database);
    let replacement = database.with_extension("rollback.part");
    if replacement.exists() {
        fs::remove_file(&replacement)?;
    }
    fs::copy(rollback, &replacement)?;
    if database.exists() {
        fs::remove_file(database)?;
    }
    fs::rename(replacement, database)?;
    Ok(())
}

fn remove_database_sidecars(database: &Path) {
    let value = database.as_os_str().to_string_lossy();
    let _ = fs::remove_file(PathBuf::from(format!("{value}-wal")));
    let _ = fs::remove_file(PathBuf::from(format!("{value}-shm")));
}

fn user_home() -> Option<PathBuf> {
    std::env::var_os("USERPROFILE")
        .or_else(|| std::env::var_os("HOME"))
        .map(PathBuf::from)
}

fn free_port() -> AppResult<u16> {
    let listener = TcpListener::bind(("127.0.0.1", 0))?;
    Ok(listener.local_addr()?.port())
}

pub(crate) fn start_backend_with_retry(candidates: &[PathBuf]) -> AppResult<(u16, Child)> {
    for attempt in 1..=5 {
        let port = free_port()?;
        let mut child = start_backend(port, candidates)?;
        match wait_backend(port) {
            Ok(()) => return Ok((port, child)),
            Err(error) if attempt == 5 => {
                let _ = child.kill();
                let _ = child.wait();
                return Err(error);
            }
            Err(error) => {
                eprintln!("后端启动失败，重试 {attempt}/5: {error}");
                let _ = child.kill();
                let _ = child.wait();
            }
        }
    }
    Err("Backend did not start.".into())
}

pub(crate) fn start_backend_on_port(port: u16, candidates: &[PathBuf]) -> AppResult<Child> {
    let mut child = start_backend(port, candidates)?;
    match wait_backend(port) {
        Ok(()) => Ok(child),
        Err(start_error) => match stop_child_checked(&mut child) {
            Ok(()) => Err(start_error),
            Err(cleanup_error) => Err(format!(
                "{start_error}; failed to clean up backend process: {cleanup_error}"
            )
            .into()),
        },
    }
}

pub(crate) fn path_candidates(resource_dir: Option<PathBuf>) -> Vec<PathBuf> {
    let mut paths = Vec::new();

    if let Ok(exe) = std::env::current_exe() {
        if let Some(dir) = exe.parent() {
            paths.push(dir.join("_up_"));
            paths.push(dir.to_path_buf());
            paths.push(dir.join("resources"));
        }
    }

    if let Some(path) = resource_dir {
        paths.push(path.join("_up_"));
        paths.push(path);
    }

    #[cfg(debug_assertions)]
    if let Some(repo_root) = Path::new(env!("CARGO_MANIFEST_DIR")).parent() {
        paths.push(repo_root.to_path_buf());
    }

    paths
}

fn start_backend(port: u16, candidates: &[PathBuf]) -> AppResult<Child> {
    let java = find_java(candidates);
    let jar = find_jar(candidates)?;
    log_desktop(format!(
        "starting backend: java={}, jar={}, port={port}",
        java.display(),
        jar.display()
    ));

    let mut command = Command::new(java);
    command
        .arg("--enable-native-access=ALL-UNNAMED")
        .stdin(Stdio::null());

    if let Some(temp_dir) = backend_temp_dir() {
        let _ = fs::create_dir_all(&temp_dir);
        command.arg(format!("-Djava.io.tmpdir={}", temp_dir.display()));
    }

    command
        .arg("-jar")
        .arg(jar)
        .arg(format!("--server.port={port}"))
        .arg("--server.address=127.0.0.1");

    if let Some((stdout, stderr)) = backend_log_files() {
        command
            .stdout(Stdio::from(stdout))
            .stderr(Stdio::from(stderr));
    } else {
        command.stdout(Stdio::null()).stderr(Stdio::null());
    }

    #[cfg(windows)]
    command.creation_flags(CREATE_NO_WINDOW);

    command.spawn().map_err(|error| error.into())
}

fn find_java(candidates: &[PathBuf]) -> PathBuf {
    for root in candidates {
        for rel in [
            "target/custom-jre/bin/java.exe",
            "custom-jre/bin/java.exe",
            "backend/custom-jre/bin/java.exe",
        ] {
            let path = root.join(rel);
            if path.exists() {
                return path;
            }
        }
    }
    PathBuf::from("java")
}

fn find_jar(candidates: &[PathBuf]) -> AppResult<PathBuf> {
    for root in candidates {
        for dir in [root.join("target"), root.clone(), root.join("backend")] {
            if let Some(jar) = find_jar_in(&dir) {
                return Ok(jar);
            }
        }
    }
    Err("Spring Boot JAR not found. Run mvnw.cmd package first.".into())
}

fn find_jar_in(dir: &Path) -> Option<PathBuf> {
    fs::read_dir(dir)
        .ok()?
        .filter_map(Result::ok)
        .find_map(|entry| {
            let path = entry.path();
            let name = path.file_name()?.to_string_lossy();
            let is_app_jar = path.extension()? == "jar"
                && !name.contains("sources")
                && !name.contains("javadoc")
                && !name.contains("plain");
            is_app_jar.then_some(path)
        })
}

fn format_paths(paths: &[PathBuf]) -> String {
    paths
        .iter()
        .map(|path| path.display().to_string())
        .collect::<Vec<_>>()
        .join("; ")
}

fn desktop_log_path() -> Option<PathBuf> {
    std::env::var_os("USERPROFILE")
        .or_else(|| std::env::var_os("HOME"))
        .map(PathBuf::from)
        .map(|home| {
            home.join(".water-management")
                .join("logs")
                .join("desktop.log")
        })
}

fn backend_temp_dir() -> Option<PathBuf> {
    std::env::var_os("USERPROFILE")
        .or_else(|| std::env::var_os("HOME"))
        .map(PathBuf::from)
        .map(|home| home.join(".water-management").join("tmp"))
}

pub(crate) fn log_desktop(message: impl AsRef<str>) {
    let Some(path) = desktop_log_path() else {
        return;
    };
    if let Some(dir) = path.parent() {
        let _ = fs::create_dir_all(dir);
    }
    if let Ok(mut file) = fs::OpenOptions::new().create(true).append(true).open(path) {
        let _ = writeln!(file, "{:?} {}", SystemTime::now(), message.as_ref());
    }
}

fn backend_log_files() -> Option<(fs::File, fs::File)> {
    let path = desktop_log_path()?;
    if let Some(dir) = path.parent() {
        let _ = fs::create_dir_all(dir);
    }
    let stdout = fs::OpenOptions::new()
        .create(true)
        .append(true)
        .open(&path)
        .ok()?;
    let stderr = fs::OpenOptions::new()
        .create(true)
        .append(true)
        .open(path)
        .ok()?;
    Some((stdout, stderr))
}

fn wait_backend(port: u16) -> AppResult<()> {
    let deadline = Instant::now() + Duration::from_secs(40);
    while Instant::now() < deadline {
        if health_check(port) {
            return Ok(());
        }
        thread::sleep(Duration::from_millis(300));
    }
    Err("Backend did not become ready within 40 seconds.".into())
}

fn health_check(port: u16) -> bool {
    let addr = SocketAddr::from(([127, 0, 0, 1], port));
    let Ok(mut stream) = TcpStream::connect_timeout(&addr, Duration::from_millis(500)) else {
        return false;
    };

    let _ = stream.set_read_timeout(Some(Duration::from_millis(800)));
    let request = "GET /api/settings/info HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n";
    if stream.write_all(request.as_bytes()).is_err() {
        return false;
    }

    let mut response = String::new();
    stream.read_to_string(&mut response).is_ok() && response.starts_with("HTTP/1.1 200")
}

fn check_data_integrity(port: u16) -> AppResult<Option<String>> {
    let addr = SocketAddr::from(([127, 0, 0, 1], port));
    let mut stream = TcpStream::connect_timeout(&addr, Duration::from_secs(5))?;
    stream.set_read_timeout(Some(Duration::from_secs(5)))?;
    let request =
        "GET /api/accounting/health-check HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n";
    stream.write_all(request.as_bytes())?;

    let mut response = String::new();
    stream.read_to_string(&mut response)?;
    if !response.starts_with("HTTP/1.1 200") {
        return Ok(None);
    }
    if let Some(body) = response.split("\r\n\r\n").nth(1) {
        if body.contains(r#""data":["#) && !body.contains(r#""data":[]"#) {
            return Ok(Some(body.to_string()));
        }
    }
    Ok(None)
}

fn accounting_health_endpoint_available(port: u16) -> bool {
    let addr = SocketAddr::from(([127, 0, 0, 1], port));
    let Ok(mut stream) = TcpStream::connect_timeout(&addr, Duration::from_secs(5)) else {
        return false;
    };
    let _ = stream.set_read_timeout(Some(Duration::from_secs(5)));
    let request =
        "GET /api/accounting/health-check HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n";
    if stream.write_all(request.as_bytes()).is_err() {
        return false;
    }
    let mut response = String::new();
    stream.read_to_string(&mut response).is_ok() && response.starts_with("HTTP/1.1 200")
}

fn notify_data_integrity_issues(window: &tauri::WebviewWindow, issues: &str) {
    notify_window_event(window, "wm-accounting-health", issues);
}

pub(crate) fn notify_window_event(window: &tauri::WebviewWindow, event: &str, detail: &str) {
    let Ok(detail) = serde_json::to_string(detail) else {
        return;
    };
    let script =
        format!("window.dispatchEvent(new CustomEvent('{event}', {{ detail: {detail} }}));");
    let _ = window.eval(&script);
}

fn read_window_state(app: &tauri::App) -> Option<WindowState> {
    let path = app.path().app_data_dir().ok()?.join("window-state.json");
    fs::read_to_string(path)
        .ok()
        .and_then(|content| serde_json::from_str(&content).ok())
}

fn save_window_state(window: &tauri::Window) {
    let Ok(position) = window.outer_position() else {
        return;
    };
    let Ok(size) = window.outer_size() else {
        return;
    };
    let Ok(dir) = window.path().app_data_dir() else {
        return;
    };
    let _ = fs::create_dir_all(&dir);
    let state = WindowState {
        x: position.x,
        y: position.y,
        width: size.width as f64,
        height: size.height as f64,
        maximized: window.is_maximized().unwrap_or(false),
    };
    if let Ok(content) = serde_json::to_string_pretty(&state) {
        let _ = fs::write(dir.join("window-state.json"), content);
    }
}

pub(crate) fn stop_backend(state: &BackendState) {
    let Some(mut guard) = state.0.lock().ok() else {
        return;
    };
    if let Some(mut child) = guard.take() {
        let _ = child.kill();
        let _ = child.wait();
    }
}

fn stop_child_checked(child: &mut Child) -> Result<(), String> {
    match child.try_wait() {
        Ok(Some(_)) => return Ok(()),
        Ok(None) => {}
        Err(error) => return Err(format!("检查后台进程状态失败: {error}")),
    }

    if let Err(kill_error) = child.kill() {
        return match child.try_wait() {
            Ok(Some(_)) => Ok(()),
            Ok(None) => Err(format!("终止后台进程失败: {kill_error}")),
            Err(status_error) => Err(format!(
                "终止后台进程失败: {kill_error}；再次检查进程状态失败: {status_error}"
            )),
        };
    }

    match child.wait() {
        Ok(_) => Ok(()),
        Err(wait_error) => match child.try_wait() {
            Ok(Some(_)) => Ok(()),
            Ok(None) => Err(format!("等待后台进程退出失败: {wait_error}")),
            Err(status_error) => Err(format!(
                "等待后台进程退出失败: {wait_error}；再次检查进程状态失败: {status_error}"
            )),
        },
    }
}

pub(crate) fn stop_backend_checked(state: &BackendState) -> Result<(), String> {
    let mut guard = state.0.lock().map_err(|_| "后端状态锁已损坏".to_string())?;
    let Some(mut child) = guard.take() else {
        return Err("未找到后台进程句柄，无法确认后台已退出".to_string());
    };
    if let Err(error) = stop_child_checked(&mut child) {
        *guard = Some(child);
        return Err(error);
    }
    Ok(())
}

#[cfg(test)]
mod restore_tests {
    use super::*;

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
    fn rejects_restore_token_outside_staging_root() {
        let root = fixture_root("token");
        assert!(resolve_restore_files(&root, "../water_meter.db").is_err());
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn resolves_only_descriptor_file_names_inside_staging_root() {
        let root = fixture_root("descriptor");
        let token = "123e4567-e89b-12d3-a456-426614174000";
        fs::write(
            root.join(format!("{token}.properties")),
            format!("staged={token}.db\nrollback={token}.rollback.db\n"),
        )
        .unwrap();
        fs::write(root.join(format!("{token}.db")), b"new").unwrap();
        fs::write(root.join(format!("{token}.rollback.db")), b"old").unwrap();

        let files = resolve_restore_files(&root, token).unwrap();

        assert!(files.staged.starts_with(&root));
        assert!(files.rollback.starts_with(&root));
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn rollback_restores_original_database_bytes() {
        let root = fixture_root("rollback");
        let database = root.join("water_meter.db");
        let rollback = root.join("rollback.db");
        fs::write(&database, b"restored-but-unhealthy").unwrap();
        fs::write(&rollback, b"original").unwrap();

        restore_rollback_file(&database, &rollback).unwrap();

        assert_eq!(fs::read(&database).unwrap(), b"original");
        let _ = fs::remove_dir_all(root);
    }
}
