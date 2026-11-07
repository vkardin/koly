import { relaunch } from "@tauri-apps/plugin-process";
import { check, type Update } from "@tauri-apps/plugin-updater";

export type { Update };

/** Returns the pending update, or null when up to date. Throws on network errors. */
export async function findUpdate(): Promise<Update | null> {
  return await check({ timeout: 20000 });
}

export async function installAndRestart(update: Update, onProgress?: (done: number, total: number | null) => void) {
  let done = 0;
  let total: number | null = null;
  await update.downloadAndInstall((event) => {
    if (event.event === "Started") total = event.data.contentLength ?? null;
    if (event.event === "Progress") done += event.data.chunkLength;
    onProgress?.(done, total);
  });
  await relaunch();
}
