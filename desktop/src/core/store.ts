import { invoke } from "@tauri-apps/api/core";
import { emit, listen } from "@tauri-apps/api/event";
import { defaultSettings, type Settings } from "./types";

export async function readJson<T>(name: string): Promise<T | null> {
  try {
    const raw = await invoke<string | null>("store_read", { name });
    return raw ? (JSON.parse(raw) as T) : null;
  } catch {
    return null;
  }
}

export async function writeJson(name: string, value: unknown): Promise<void> {
  try {
    await invoke("store_write", { name, contents: JSON.stringify(value) });
  } catch (e) {
    console.error("store_write failed", name, e);
  }
}

function merge(saved: Partial<Settings> | null): Settings {
  const d = defaultSettings();
  if (!saved) return d;
  return { ...d, ...saved, notify: { ...d.notify, ...(saved.notify ?? {}) } };
}

export async function loadSettings(): Promise<Settings> {
  return merge(await readJson<Partial<Settings>>("settings.json"));
}

/** Persists settings and notifies every window so changes apply immediately. */
export async function saveSettings(s: Settings): Promise<void> {
  await writeJson("settings.json", s);
  await emit("settings-changed", s);
}

export function onSettingsChanged(cb: (s: Settings) => void) {
  return listen<Settings>("settings-changed", (e) => cb(merge(e.payload)));
}
