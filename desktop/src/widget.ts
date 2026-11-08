import "./styles/fonts.css";
import "./styles/base.css";
import "./styles/widget.css";
import { invoke } from "@tauri-apps/api/core";
import { emit, listen } from "@tauri-apps/api/event";
import { LogicalSize, PhysicalPosition, availableMonitors, getCurrentWindow } from "@tauri-apps/api/window";
import { ensureDemoRegions, loadCachedState, refresh, type DataState } from "./core/data";
import { agoText, setLang, t, trayLabels } from "./core/i18n";
import { checkAfterRefresh, checkUpcoming } from "./core/notify";
import { esc, logoMark, renderBar, renderRing } from "./core/render";
import { formatCountdown, formatHm, kyivNow, placeStatus, type PlaceStatus } from "./core/schedule";
import { loadSettings, onSettingsChanged, saveSettings, writeJson } from "./core/store";
import { applyTheme, onSystemThemeChange } from "./core/theme";
import { findUpdate } from "./core/update";
import type { Place, Settings, WidgetView, WindowMode } from "./core/types";

const win = getCurrentWindow();
const root = document.getElementById("root")!;
const MARGIN = 10; // transparent margin around the widget, in CSS px

let settings: Settings;
let data: DataState;
let refreshing = false;
let refreshTimer: number | undefined;
let lastTraySig = "";
let updateVersion: string | null = null;

function mainPlace(): Place | undefined {
  return settings.places.find((p) => p.id === settings.mainPlaceId) ?? settings.places[0];
}

function regionIds(): string[] {
  return settings.places.map((p) => p.regionId);
}

function stateLabel(state: PlaceStatus["state"]): string {
  return state === "on" ? t("lightOn") : state === "off" ? t("lightOff") : t("lightMaybe");
}

function stillText(ps: PlaceStatus): string {
  return ps.state === "on" ? t("stillOn") : ps.state === "off" ? t("stillOff") : t("stillMaybe");
}

function inText(ps: PlaceStatus): string {
  const next = ps.nextState;
  return next === "off" ? t("offIn") : next === "maybe" ? t("maybeIn") : t("onIn");
}

function placeIssue(place: Place): string | null {
  const region = data.regions[place.regionId];
  if (!region) return t("noData");
  if (region.status === "no_schedules") return t("noSchedules");
  // Never present a missing day as "power on all day".
  if (!region.days.some((d) => d.date === kyivNow().date)) return t("noData");
  return null;
}

function statusFooter(): string {
  const parts = [t("updatedAgo", { ago: agoText(data.checkedAt) })];
  if (data.demo) parts.push(`<b class="warn">${esc(t("demo"))}</b>`);
  else if (data.offline) parts.push(`<b class="warn">${esc(t("offline"))}</b>`);
  const stale = settings.places.some((p) => data.regions[p.regionId]?.status === "stale");
  if (stale && !data.demo) parts.push(`<b class="warn">${esc(t("stale"))}</b>`);
  return parts.join(" · ");
}

function emergencyBanner(places: Place[]): string {
  const active = places.find((p) => data.regions[p.regionId]?.emergency?.active);
  if (!active) return "";
  const note = data.regions[active.regionId].emergency.note;
  return `<div class="emergency" role="alert">⚡ ${esc(t("emergency"))}${note ? ` — ${esc(note)}` : ""}</div>`;
}

/** Short warnings for the compact views, which have no footer: emergency, offline, stale, demo. */
function compactAlerts(place: Place): string {
  const region = data.regions[place.regionId];
  const items: string[] = [];
  if (region?.emergency?.active) items.push(`⚡ ${t("emergency")}`);
  if (data.demo) items.push(t("demo"));
  else if (data.offline) items.push(t("offline"));
  if (!data.demo && region?.status === "stale") items.push(t("stale"));
  return items.length ? `<div class="compact-alert" role="status">${esc(items.join(" · "))}</div>` : "";
}

function changeAtText(ps: PlaceStatus): string {
  return ps.changeAt != null ? t("untilTime", { time: formatHm(ps.changeAt % 1440) }) : "";
}

function settingsButton(): string {
  return `<button class="icon-btn" data-action="settings" title="${esc(t("openSettings"))}" aria-label="${esc(t("openSettings"))}">
    <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true"><path d="M12 15.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7Zm7.4-2.5a7.6 7.6 0 0 0 0-2l2-1.6-2-3.4-2.4 1a7.4 7.4 0 0 0-1.7-1L15 3.5h-4l-.4 2.5a7.4 7.4 0 0 0-1.7 1l-2.4-1-2 3.4 2 1.6a7.6 7.6 0 0 0 0 2l-2 1.6 2 3.4 2.4-1c.5.4 1.1.8 1.7 1l.4 2.5h4l.4-2.5c.6-.2 1.2-.6 1.7-1l2.4 1 2-3.4-2-1.6Z" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/></svg>
  </button>`;
}

function refreshButton(): string {
  return `<button class="icon-btn${refreshing ? " spinning" : ""}" data-action="refresh" title="${esc(t("refresh"))}" aria-label="${esc(t("refresh"))}" aria-busy="${refreshing}" aria-disabled="${refreshing}">
    <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true"><path d="M20 12a8 8 0 1 1-2.34-5.66M20 4v5h-5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
  </button>`;
}

function emptyState(): string {
  return `<div class="empty"><div>${esc(t("noPlaces"))}</div>
    <button class="btn" data-action="settings">${esc(t("addPlace"))}</button></div>`;
}

function renderStrip(): string {
  const place = mainPlace();
  if (!place) return `<div class="widget strip">${emptyState()}</div>`;
  const now = kyivNow();
  const issue = placeIssue(place);
  const ps = placeStatus(data.regions[place.regionId], place.group, now);
  const count = issue ? "—" : ps.untilChange != null ? formatCountdown(ps.untilChange) : "—";
  const caption = issue ?? (ps.untilChange != null ? inText(ps) : t("noChangesToday"));
  const tip = issue ?? [stateLabel(ps.state), changeAtText(ps)].filter(Boolean).join(" · ");
  return `<div class="widget strip" title="${esc(tip)}">
    <span class="dot state-${issue ? "none" : ps.state}" aria-hidden="true"></span>
    <div class="strip-text">
      <div class="strip-place">${esc(place.label)}</div>
      <div class="strip-caption">${esc(issue ? caption : `${stateLabel(ps.state)} · ${caption}`)}</div>
      ${compactAlerts(place)}
    </div>
    <div class="count state-${issue ? "none" : ps.state}">${count}</div>
    <div class="strip-actions">${refreshButton()}${settingsButton()}</div>
  </div>`;
}

function renderCardPlace(place: Place): string {
  const now = kyivNow();
  const issue = placeIssue(place);
  const region = data.regions[place.regionId];
  const ps = placeStatus(region, place.group, now);
  const isMain = place.id === (mainPlace()?.id ?? "");
  let countLine: string;
  if (issue) countLine = `<span class="muted">${esc(issue)}</span>`;
  else if (ps.untilChange != null)
    countLine = `<span class="muted">${esc(stillText(ps))}</span> <b class="count-sm state-${ps.state}">${formatCountdown(ps.untilChange)}</b> <span class="muted change-at">${esc(changeAtText(ps))}</span>`;
  else countLine = `<span class="muted">${esc(t("noChangesToday"))}</span>`;
  const bar = !issue && ps.hasToday ? renderBar(ps.today, { nowMinutes: now.minutes, labels: isMain, durations: isMain, compact: !isMain }) : "";
  const tomorrow =
    settings.showTomorrow && !issue && ps.tomorrow
      ? `<div class="tomorrow"><span class="muted small">${esc(t("tomorrow"))}</span>${renderBar(ps.tomorrow, { compact: true })}</div>`
      : "";
  return `<div class="place">
    <div class="place-head">
      <span class="dot state-${issue ? "none" : ps.state}" aria-hidden="true"></span>
      <span class="place-label">${esc(place.label)}</span>
      <span class="place-count">${countLine}</span>
    </div>
    ${bar}${tomorrow}
  </div>`;
}

function renderCard(): string {
  const places = settings.places;
  return `<div class="widget card">
    <div class="card-head">
      <div class="brand">${logoMark(20)}<span>Коли <em>Є</em></span></div>
      <div class="head-right">
        <span class="muted small">${esc(agoText(data.checkedAt))}</span>
        ${refreshButton()}
        ${settingsButton()}
      </div>
    </div>
    ${emergencyBanner(places)}
    ${data.demo ? `<div class="demo-note">${esc(t("demoLong"))}</div>` : ""}
    ${places.length ? places.map(renderCardPlace).join("") : emptyState()}
    ${updateVersion ? `<button class="update-link" data-action="update">${esc(t("updateAvailable", { v: updateVersion }))}</button>` : ""}
    <div class="card-foot muted small">${statusFooter()} · ${esc(t("unofficial"))}</div>
  </div>`;
}

function renderRingView(): string {
  const place = mainPlace();
  if (!place) return `<div class="widget ring-view">${emptyState()}</div>`;
  const now = kyivNow();
  const issue = placeIssue(place);
  const ps = placeStatus(data.regions[place.regionId], place.group, now);
  const center = issue ? "—" : ps.untilChange != null ? formatCountdown(ps.untilChange) : "—";
  const sub = issue ?? (ps.changeAt != null ? inText(ps) : t("noChangesToday"));
  const foot = issue ? "" : [stateLabel(ps.state), changeAtText(ps)].filter(Boolean).join(" · ");
  return `<div class="widget ring-view">
    <div class="ring-actions">${refreshButton()}${settingsButton()}</div>
    <div class="ring-head"><span class="place-label">${esc(place.label)}</span></div>
    ${renderRing(issue ? [] : ps.today, now.minutes, center, sub, issue ? "none" : ps.state)}
    ${foot ? `<div class="ring-foot muted small">${esc(foot)}</div>` : ""}
    ${compactAlerts(place)}
  </div>`;
}

const renderers: Record<WidgetView, () => string> = { strip: renderStrip, card: renderCard, ring: renderRingView };

let lastSize = "";
async function fitWindow() {
  const el = root.firstElementChild as HTMLElement | null;
  if (!el) return;
  const rect = el.getBoundingClientRect();
  const w = Math.ceil(rect.width + MARGIN * 2);
  const h = Math.ceil(rect.height + MARGIN * 2);
  const key = `${w}x${h}`;
  if (key === lastSize) return;
  lastSize = key;
  await win.setSize(new LogicalSize(w, h));
}

function render() {
  if (!settings || !data) return;
  document.body.dataset.view = settings.view;
  document.body.classList.toggle("locked", settings.lockPosition);
  const focused = (document.activeElement as HTMLElement | null)?.dataset.action;
  root.innerHTML = renderers[settings.view]();
  if (focused) root.querySelector<HTMLElement>(`[data-action="${focused}"]`)?.focus();
  void fitWindow();
}

async function syncTray() {
  const sig = JSON.stringify([settings.lang, settings.view, settings.windowMode, settings.trayIcon]);
  if (sig === lastTraySig) return;
  lastTraySig = sig;
  try {
    await invoke("update_tray", { labels: trayLabels(), view: settings.view, mode: settings.windowMode, visible: settings.trayIcon });
  } catch (e) {
    console.error(e);
  }
}

async function applyWindowMode(mode: WindowMode) {
  if (mode === "top") {
    await win.setAlwaysOnBottom(false);
    await win.setAlwaysOnTop(true);
  } else {
    await win.setAlwaysOnTop(false);
    await win.setAlwaysOnBottom(true);
  }
}

async function doRefresh() {
  if (refreshing) return;
  refreshing = true;
  render();
  try {
    data = await refresh(data, regionIds());
    await emit("data-updated", data);
    await checkAfterRefresh(settings, data);
  } finally {
    refreshing = false;
    render();
  }
}

function scheduleRefresh() {
  if (refreshTimer) clearInterval(refreshTimer);
  refreshTimer = undefined;
  if (settings.refreshMinutes > 0) refreshTimer = window.setInterval(doRefresh, settings.refreshMinutes * 60_000);
}

async function applySettings(next: Settings, initial = false) {
  const prev = settings;
  settings = next;
  setLang(settings.lang);
  applyTheme(settings);
  if (initial || prev.windowMode !== settings.windowMode) await applyWindowMode(settings.windowMode);
  if (initial || prev.refreshMinutes !== settings.refreshMinutes) scheduleRefresh();
  const newRegions = settings.places.filter((p) => !data.regions[p.regionId]).map((p) => p.regionId);
  if (newRegions.length) {
    if (data.demo) data = ensureDemoRegions(data, newRegions);
    else if (!initial) void doRefresh();
  }
  await syncTray();
  lastSize = "";
  render();
}

async function savePatch(patch: Partial<Settings>) {
  await saveSettings({ ...settings, ...patch });
}

async function restorePosition() {
  const pos = settings.position;
  if (!pos) return;
  try {
    const monitors = await availableMonitors();
    const visible = monitors.some((m) => {
      const { x, y } = m.position;
      const { width, height } = m.size;
      return pos.x >= x - 50 && pos.x < x + width - 50 && pos.y >= y - 10 && pos.y < y + height - 50;
    });
    if (visible) await win.setPosition(new PhysicalPosition(pos.x, pos.y));
  } catch (e) {
    console.warn(e);
  }
}

function wireInput() {
  root.addEventListener("click", (e) => {
    const btn = (e.target as HTMLElement).closest<HTMLElement>("[data-action]");
    if (!btn) return;
    e.stopPropagation();
    if (btn.dataset.action === "refresh") void doRefresh();
    if (btn.dataset.action === "settings") void invoke("open_settings");
    if (btn.dataset.action === "update") void invoke("open_settings", { section: "about" });
  });
  root.addEventListener("mousedown", (e) => {
    if (e.button !== 0 || settings.lockPosition) return;
    if ((e.target as HTMLElement).closest("button, a, select, input")) return;
    void win.startDragging();
  });
  window.addEventListener("contextmenu", (e) => {
    e.preventDefault();
    void invoke("open_settings");
  });
  let moveTimer: number | undefined;
  void win.onMoved(({ payload }) => {
    clearTimeout(moveTimer);
    moveTimer = window.setTimeout(() => {
      if (settings.position?.x === payload.x && settings.position?.y === payload.y) return;
      void savePatch({ position: { x: payload.x, y: payload.y } });
    }, 600);
  });
}

async function onTrayAction(id: string) {
  if (id === "refresh") return doRefresh();
  if (id.startsWith("view-")) return savePatch({ view: id.slice(5) as WidgetView });
  if (id.startsWith("mode-")) return savePatch({ windowMode: id.slice(5) as WindowMode });
}

let lastDate = kyivNow().date;
function tick() {
  const today = kyivNow().date;
  if (today !== lastDate) {
    lastDate = today;
    if (data.demo) data = ensureDemoRegions(data, regionIds());
  }
  render();
  void checkUpcoming(settings, data);
}

function logError(where: string, e: unknown) {
  console.error(where, e);
  void writeJson("last-error.json", { where, at: new Date().toISOString(), error: String((e as Error)?.stack ?? e) });
}

async function checkUpdates() {
  try {
    const u = await findUpdate();
    updateVersion = u?.version ?? null;
    render();
  } catch (e) {
    console.warn("update check failed", e);
  }
}

async function main() {
  settings = await loadSettings();
  data = await loadCachedState();
  if (data.demo) data = ensureDemoRegions(data, regionIds());
  try {
    await applySettings(settings, true);
    await restorePosition();
  } catch (e) {
    logError("startup", e);
  }
  wireInput();
  await win.show();

  await onSettingsChanged((s) => void applySettings(s));
  await listen<string>("tray-action", (e) => void onTrayAction(e.payload));
  await listen("refresh-request", () => void doRefresh());
  await listen("data-request", () => void emit("data-updated", data));
  onSystemThemeChange(() => {
    applyTheme(settings);
    render();
  });
  window.setInterval(tick, 20_000);
  new ResizeObserver(() => void fitWindow()).observe(root);
  void doRefresh();
  window.setTimeout(checkUpdates, 30_000);
  window.setInterval(checkUpdates, 12 * 3600_000);
}

window.addEventListener("error", (e) => logError("window", e.error ?? e.message));
window.addEventListener("unhandledrejection", (e) => logError("promise", e.reason));
void main().catch((e) => logError("main", e));
