import type { Settings } from "./types";

const media = window.matchMedia("(prefers-color-scheme: dark)");

export function applyTheme(s: Pick<Settings, "theme" | "opacity" | "scale">, applyScale = true) {
  const root = document.documentElement;
  const dark = s.theme === "dark" || (s.theme === "system" && media.matches);
  root.dataset.theme = dark ? "dark" : "light";
  root.style.setProperty("--bg-alpha", String(Math.min(100, Math.max(40, s.opacity)) / 100));
  if (applyScale) root.style.fontSize = `${(16 * Math.min(125, Math.max(90, s.scale))) / 100}px`;
}

export function onSystemThemeChange(cb: () => void) {
  media.addEventListener("change", cb);
}
