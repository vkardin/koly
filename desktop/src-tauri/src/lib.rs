use std::collections::HashMap;
use std::fs;
use std::path::PathBuf;

use tauri::menu::{CheckMenuItem, Menu, MenuItem, PredefinedMenuItem, Submenu};
use tauri::tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent};
use tauri::{AppHandle, Emitter, Manager, Runtime, WebviewUrl, WebviewWindowBuilder, WindowEvent};

const MAIN: &str = "main";
const SETTINGS: &str = "settings";
const TRAY_ID: &str = "koly-tray";

fn data_dir<R: Runtime>(app: &AppHandle<R>) -> Result<PathBuf, String> {
    let dir = app.path().app_data_dir().map_err(|e| e.to_string())?;
    fs::create_dir_all(&dir).map_err(|e| e.to_string())?;
    Ok(dir)
}

fn safe_name(name: &str) -> Result<&str, String> {
    let ok = !name.is_empty()
        && name.len() <= 64
        && name
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || c == '-' || c == '_' || c == '.')
        && !name.starts_with('.');
    if ok {
        Ok(name)
    } else {
        Err("invalid file name".into())
    }
}

#[tauri::command]
fn store_read(app: AppHandle, name: String) -> Result<Option<String>, String> {
    let path = data_dir(&app)?.join(safe_name(&name)?);
    match fs::read_to_string(path) {
        Ok(s) => Ok(Some(s)),
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => Ok(None),
        Err(e) => Err(e.to_string()),
    }
}

#[tauri::command]
fn store_write(app: AppHandle, name: String, contents: String) -> Result<(), String> {
    let dir = data_dir(&app)?;
    let name = safe_name(&name)?;
    let tmp = dir.join(format!("{name}.tmp"));
    fs::write(&tmp, contents).map_err(|e| e.to_string())?;
    fs::rename(&tmp, dir.join(name)).map_err(|e| e.to_string())
}

fn show_settings<R: Runtime>(app: &AppHandle<R>, section: Option<String>) -> Result<(), String> {
    let section = section.filter(|s| s.chars().all(|c| c.is_ascii_lowercase()));
    if let Some(w) = app.get_webview_window(SETTINGS) {
        if let Some(s) = &section {
            let _ = app.emit_to(SETTINGS, "settings-section", s.clone());
        }
        let _ = w.unminimize();
        let _ = w.show();
        let _ = w.set_focus();
        return Ok(());
    }
    let url = match &section {
        Some(s) => format!("settings.html?section={s}"),
        None => "settings.html".to_string(),
    };
    WebviewWindowBuilder::new(app, SETTINGS, WebviewUrl::App(url.into()))
        .title("Коли Є")
        .inner_size(900.0, 660.0)
        .min_inner_size(760.0, 520.0)
        .center()
        .background_color(tauri::webview::Color(15, 20, 34, 255))
        .build()
        .map(|_| ())
        .map_err(|e| e.to_string())
}

// Must be async: creating a webview window from a synchronous command
// deadlocks the event loop on Windows.
#[tauri::command]
async fn open_settings(app: AppHandle, section: Option<String>) -> Result<(), String> {
    show_settings(&app, section)
}

fn toggle_main<R: Runtime>(app: &AppHandle<R>) {
    if let Some(w) = app.get_webview_window(MAIN) {
        if w.is_visible().unwrap_or(false) {
            let _ = w.hide();
        } else {
            let _ = w.show();
        }
    }
}

fn label<'a>(labels: &'a HashMap<String, String>, key: &str, fallback: &'a str) -> &'a str {
    labels.get(key).map(String::as_str).unwrap_or(fallback)
}

fn build_menu<R: Runtime>(
    app: &AppHandle<R>,
    labels: &HashMap<String, String>,
    view: &str,
    mode: &str,
) -> tauri::Result<Menu<R>> {
    let show_hide = MenuItem::with_id(app, "show-hide", label(labels, "showHide", "Показати / сховати"), true, None::<&str>)?;
    let refresh = MenuItem::with_id(app, "refresh", label(labels, "refresh", "Оновити зараз"), true, None::<&str>)?;
    let mode_menu = Submenu::with_items(
        app,
        label(labels, "mode", "Режим вікна"),
        true,
        &[
            &CheckMenuItem::with_id(app, "mode-top", label(labels, "modeTop", "Поверх усіх вікон"), true, mode == "top", None::<&str>)?,
            &CheckMenuItem::with_id(app, "mode-desktop", label(labels, "modeDesktop", "На робочому столі"), true, mode == "desktop", None::<&str>)?,
        ],
    )?;
    let view_menu = Submenu::with_items(
        app,
        label(labels, "view", "Вид віджета"),
        true,
        &[
            &CheckMenuItem::with_id(app, "view-strip", label(labels, "viewStrip", "Смужка"), true, view == "strip", None::<&str>)?,
            &CheckMenuItem::with_id(app, "view-card", label(labels, "viewCard", "Картка"), true, view == "card", None::<&str>)?,
            &CheckMenuItem::with_id(app, "view-ring", label(labels, "viewRing", "Кільце"), true, view == "ring", None::<&str>)?,
        ],
    )?;
    let settings = MenuItem::with_id(app, "settings", label(labels, "settings", "Налаштування"), true, None::<&str>)?;
    let quit = MenuItem::with_id(app, "quit", label(labels, "quit", "Вийти"), true, None::<&str>)?;
    Menu::with_items(
        app,
        &[
            &show_hide,
            &refresh,
            &PredefinedMenuItem::separator(app)?,
            &mode_menu,
            &view_menu,
            &PredefinedMenuItem::separator(app)?,
            &settings,
            &PredefinedMenuItem::separator(app)?,
            &quit,
        ],
    )
}

#[tauri::command]
fn update_tray(
    app: AppHandle,
    labels: HashMap<String, String>,
    view: String,
    mode: String,
    visible: bool,
) -> Result<(), String> {
    let tray = app.tray_by_id(TRAY_ID).ok_or("tray not found")?;
    let menu = build_menu(&app, &labels, &view, &mode).map_err(|e| e.to_string())?;
    tray.set_menu(Some(menu)).map_err(|e| e.to_string())?;
    tray.set_visible(visible).map_err(|e| e.to_string())
}

fn create_tray<R: Runtime>(app: &AppHandle<R>) -> tauri::Result<()> {
    let menu = build_menu(app, &HashMap::new(), "card", "top")?;
    let mut builder = TrayIconBuilder::with_id(TRAY_ID)
        .tooltip("Коли Є")
        .menu(&menu)
        .show_menu_on_left_click(false)
        .on_menu_event(|app, event| match event.id().as_ref() {
            "show-hide" => toggle_main(app),
            "settings" => {
                let _ = show_settings(app, None);
            }
            "quit" => app.exit(0),
            other => {
                let _ = app.emit_to(MAIN, "tray-action", other.to_string());
            }
        })
        .on_tray_icon_event(|tray, event| {
            if let TrayIconEvent::Click {
                button: MouseButton::Left,
                button_state: MouseButtonState::Up,
                ..
            } = event
            {
                toggle_main(tray.app_handle());
            }
        });
    if let Some(icon) = app.default_window_icon() {
        builder = builder.icon(icon.clone());
    }
    builder.build(app)?;
    Ok(())
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_single_instance::init(|app, _args, _cwd| {
            if let Some(w) = app.get_webview_window(MAIN) {
                let _ = w.show();
                let _ = w.set_focus();
            }
        }))
        .plugin(tauri_plugin_autostart::init(
            tauri_plugin_autostart::MacosLauncher::LaunchAgent,
            Some(vec!["--autostart"]),
        ))
        .plugin(tauri_plugin_notification::init())
        .plugin(tauri_plugin_opener::init())
        .plugin(tauri_plugin_updater::Builder::new().build())
        .plugin(tauri_plugin_process::init())
        .invoke_handler(tauri::generate_handler![store_read, store_write, open_settings, update_tray])
        .setup(|app| {
            create_tray(app.handle())?;
            Ok(())
        })
        .on_window_event(|window, event| {
            if window.label() == MAIN {
                if let WindowEvent::CloseRequested { api, .. } = event {
                    api.prevent_close();
                    let _ = window.hide();
                }
            }
        })
        .run(tauri::generate_context!())
        .expect("error while running the application");
}
