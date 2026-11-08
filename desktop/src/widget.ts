import "./styles/fonts.css";
import "./styles/base.css";
import "./styles/widget.css";
import { invoke } from "@tauri-apps/api/core";
import { emit, listen } from "@tauri-apps/api/event";
import { CheckMenuItem, Menu, MenuItem, PredefinedMenuItem, Submenu } from "@tauri-apps/api/menu";
import { LogicalSize, PhysicalPosition, availableMonitors, getCurrentWindow } from "@tauri-apps/api/window";
import { ensureDemoRegions, loadCachedState, refresh, type DataState } from "./core/data";
import { agoText, durationText, setLang, t, trayLabels, type StringKey } from "./core/i18n";
import { checkAfterRefresh, checkUpcoming } from "./core/notify";
import { esc, logoMark, renderBar, renderRing } from "./core/render";
import { formatCountdown, formatHm, kyivNow, placeStatus, type PlaceStatus } from "./core/schedule";
import { loadSettings, onSettingsChanged, readJson, saveSettings, writeJson } from "./core/store";
import { applyTheme, onSystemThemeChange } from "./core/theme";
import { findUpdate } from "./core/update";
import type { Place, Settings, WidgetView, WindowMode } from "./core/types";

const win = getCurrentWindow();
const root = document.getElementById("root")!;
const MARGIN = 10; // transparent margin around the widget, in CSS px

let settings: Settings;
let data: DataState;
let refreshing = false;
/** Epoch ms of the last refresh attempt, successful or not. */
let lastAttempt = 0;
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

/** Reasons to distrust what the widget shows, most important first. */
function dataWarnings(places: Place[]): string[] {
  const out: string[] = [];
  if (data.demo) out.push(t("demo"));
  else if (data.offline) out.push(t("offline"));
  if (!data.demo && places.some((p) => data.regions[p.regionId]?.status === "stale")) out.push(t("stale"));
  return out;
}

/** Small badge for the one-place views, which have no footer. */
function warningBadge(place: Place): string {
  const w = dataWarnings([place])[0];
  if (!w) return "";
  return `<span class="badge" title="${esc(`${t("updatedAgo", { ago: agoText(data.checkedAt) })} · ${w}`)}">${esc(w)}</span>`;
}

function statusFooter(): string {
  const parts = dataWarnings(settings.places).map((w) => `<b class="warn">${esc(w)}</b>`);
  parts.push(esc(t("unofficial")));
  return parts.join(" · ");
}

/** "до 18:00" for the moment the current state ends. */
function untilText(ps: PlaceStatus): string {
  return ps.changeAt != null ? t("untilTime", { time: formatHm(ps.changeAt) }) : "";
}

/** Full sentence for tooltips and screen readers: the big "2:14" alone reads like a clock time. */
function spokenStatus(place: Place, ps: PlaceStatus, issue: string | null): string {
  if (issue) return `${place.label}: ${issue}`;
  if (ps.untilChange == null) return `${place.label}: ${stateLabel(ps.state)}, ${t("noChangesToday")}`;
  return `${place.label}: ${t("stateUntil", { state: stateLabel(ps.state), time: formatHm(ps.changeAt!) })}, ${inText(ps)} ${durationText(ps.untilChange)}`;
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
      ? `<div class="tomorrow"><span class="tomorrow-label muted small">${esc(t("tomorrow"))}</span>${renderBar(ps.tomorrow, { compact: true })}</div>`
      : "";
  const spoken = spokenStatus(place, ps, issue);
  return `<div class="place">
    <div class="place-head" title="${esc(spoken)}">
      <span class="dot state-${issue ? "none" : ps.state}" aria-hidden="true"></span>
      <span class="place-label" aria-hidden="true">${esc(place.label)}</span>
      <span class="place-count" aria-hidden="true">${countLine}</span>
      <span class="sr-only">${esc(spoken)}</span>
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
        <span class="muted small" title="${esc(t("updatedAgo", { ago: agoText(data.checkedAt) }))}">${esc(agoText(data.checkedAt))}</span>
        ${refreshButton()}
        ${settingsButton()}
      </div>
    </div>
    ${emergencyBanner(places)}
    ${data.demo ? `<div class="demo-note">${esc(t("demoLong"))}</div>` : ""}
    ${places.length ? places.map(renderCardPlace).join("") : emptyState()}
    ${updateVersion ? `<button class="update-link" data-action="update">${esc(t("updateAvailable", { v: updateVersion }))}</button>` : ""}
    <div class="card-foot muted small">${statusFooter()}</div>
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
let lastHtml = "";
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
  lastAttempt = Date.now();
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

/**
 * Auto-refresh is checked on every tick instead of a fixed interval, so it
 * catches up right after the PC wakes from sleep. While offline it retries
 * every 2 minutes to recover quickly.
 */
function refreshDue(): boolean {
  if (settings.refreshMinutes <= 0) return false;
  const every = data.offline ? Math.min(2, settings.refreshMinutes) : settings.refreshMinutes;
  return Date.now() - lastAttempt >= every * 60_000;
}

async function applySettings(next: Settings, initial = false) {
  const prev = settings;
  settings = next;
  setLang(settings.lang);
  applyTheme(settings);
  if (initial || prev.windowMode !== settings.windowMode) await applyWindowMode(settings.windowMode);
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

let ctxMenu: Menu | null = null;

/** Right-click menu: the everyday actions without opening the tray. */
async function showContextMenu() {
  const check = (id: string, key: StringKey, checked: boolean, action: () => void) =>
    CheckMenuItem.new({ id, text: t(key), checked, action });
  const views: Array<[WidgetView, StringKey]> = [
    ["strip", "viewStrip"],
    ["card", "viewCard"],
    ["ring", "viewRing"],
  ];
  const modes: Array<[WindowMode, StringKey]> = [
    ["top", "placementTop"],
    ["desktop", "placementDesktop"],
  ];
  const menu = await Menu.new({
    items: [
      await MenuItem.new({ id: "refresh", text: t("refreshNow"), action: () => void doRefresh() }),
      await PredefinedMenuItem.new({ item: "Separator" }),
      await Submenu.new({
        text: t("trayView"),
        items: await Promise.all(views.map(([v, k]) => check(`view-${v}`, k, settings.view === v, () => void savePatch({ view: v })))),
      }),
      await Submenu.new({
        text: t("trayMode"),
        items: await Promise.all(modes.map(([m, k]) => check(`mode-${m}`, k, settings.windowMode === m, () => void savePatch({ windowMode: m })))),
      }),
      await check("lock", "lockPosition", settings.lockPosition, () => void savePatch({ lockPosition: !settings.lockPosition })),
      await PredefinedMenuItem.new({ item: "Separator" }),
      await MenuItem.new({ id: "settings", text: t("traySettings"), action: () => void invoke("open_settings") }),
      await MenuItem.new({ id: "hide", text: t("hideWidget"), action: () => void win.hide() }),
    ],
  });
  const previous = ctxMenu;
  ctxMenu = menu;
  void previous?.close().catch(() => undefined);
  await menu.popup();
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
    showContextMenu().catch((err) => {
      console.warn("context menu failed", err);
      void invoke("open_settings");
    });
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
  if (refreshDue()) void doRefresh();
  render();
  void checkUpcoming(settings, data);
}

/** Ticks right after each full minute, when every countdown changes. */
function scheduleTick() {
  const delay = 60_000 - (Date.now() % 60_000) + 250;
  window.setTimeout(() => {
    tick();
    scheduleTick();
  }, delay);
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
  const firstRun = (await readJson("settings.json")) === null;
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
  // First launch: go straight to adding a place instead of an empty widget.
  if (firstRun && !settings.places.length) void invoke("open_settings", { section: "places" });

  await onSettingsChanged((s) => void applySettings(s));
  await listen<string>("tray-action", (e) => void onTrayAction(e.payload));
  await listen("refresh-request", () => void doRefresh());
  await listen("data-request", () => void emit("data-updated", data));
  onSystemThemeChange(() => {
    applyTheme(settings);
    render();
  });
  scheduleTick();
  window.addEventListener("online", () => void doRefresh());
  new ResizeObserver(() => void fitWindow()).observe(root);
  void doRefresh();
  window.setTimeout(checkUpdates, 30_000);
  window.setInterval(checkUpdates, 12 * 3600_000);
}

window.addEventListener("error", (e) => logError("window", e.error ?? e.message));
window.addEventListener("unhandledrejection", (e) => logError("promise", e.reason));
void main().catch((e) => logError("main", e));
