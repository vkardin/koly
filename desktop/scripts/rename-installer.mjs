// Copies the NSIS installer to release/koly-widget-<version>-setup.exe.
import { copyFileSync, mkdirSync, readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";

const conf = JSON.parse(readFileSync("src-tauri/tauri.conf.json", "utf8"));
const dir = "src-tauri/target/release/bundle/nsis";
const exe = readdirSync(dir).find((f) => f.endsWith(`${conf.version}_x64-setup.exe`));
if (!exe) throw new Error(`installer for ${conf.version} not found in ${dir}`);
mkdirSync("release", { recursive: true });
const target = join("release", `koly-widget-${conf.version}-setup.exe`);
copyFileSync(join(dir, exe), target);
console.log(`-> ${target}`);
