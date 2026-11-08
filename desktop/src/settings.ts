import "./styles/fonts.css";
import "./styles/base.css";
import "./styles/settings.css";
import { emit, listen } from "@tauri-apps/api/event";
import { disable as disableAutostart, enable as enableAutostart, isEnabled as autostartEnabled } from "@tauri-apps/plugin-autostart";
import { openUrl } from "@tauri-apps/plugin-opener";
import { APP_VERSION, loadCachedState, type DataState } from "./core/data";
import { LANGS, agoText, getLang, setLang, t, type StringKey } from "./core/i18n";
import { sendTest } from "./core/notify";
import { DEMO_GROUPS, REGION_NAMES } from "./core/regions";
import { esc, logoMark } from "./core/render";
import { loadSettings, onSettingsChanged, saveSettings } from "./core/store";
import { applyTheme, onSystemThemeChange } from "./core/theme";
import { findUpdate, installAndRestart, type Update } from "./core/update";
import { MAX_PLACES, defaultSettings, type LocalizedName, type Place, type Settings } from "./core/types";

type Section = "appearance" | "places" | "notifications" | "general" | "about";
const SECTIONS: Array<{ id: Section; key: StringKey; icon: string }> = [
  { id: "appearance", key: "secAppearance", icon: "M4 5h16v10H4zM9 19h6M12 15v4" },
  { id: "places", key: "secPlaces", icon: "M12 21s-6-5.5-6-11a6 6 0 0 1 12 0c0 5.5-6 11-6 11Zm0-8.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5Z" },
  { id: "notifications", key: "secNotifications", icon: "M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15L6 16Zm4 4h4" },
  { id: "general", key: "secGeneral", icon: "M4 7h10M18 7h2M4 17h4M12 17h8M14 5v4M8 15v4" },
  { id: "about", key: "secAbout", icon: "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Zm0-10v6m0-9.5v.5" },
];

const PROJECT_URL = "https://github.com/vkardin/koly";
const SITE_URL = "https://vkardin.github.io";
const PRIVACY_URL = "https://vkardin.github.io/koly/privacy/";

const app = document.getElementById("app")!;
let settings: Settings;
let data: DataState;
let section: Section = (new URLSearchParams(location.search).get("section") as Section) || "appearance";
let editingId: string | null = null;
let autostartOn = false;
type UpdateState =
  | { kind: "idle" }
  | { kind: "checking" }
  | { kind: "latest" }
  | { kind: "available"; update: Update }
  | { kind: "installing"; progress: string }
  | { kind: "error" };
let updateState: UpdateState = { kind: "idle" };

type UndoState = { label: string; places: Place[]; mainPlaceId: string | null };
let undo: UndoState | null = null;
let undoTimer: number | undefined;

function setUndo(next: UndoState | null) {
  undo = next;
  clearTimeout(undoTimer);
  if (next)
    undoTimer = window.setTimeout(() => {
      undo = null;
      if (section === "places" && !busy()) render();
    }, 8000);
}

function undoBar(): string {
  if (!undo) return "";
  return `<div class="undo-bar" role="status"><span>${esc(t("placeRemoved", { name: undo.label }))}</span>
    <button class="btn ghost sm" data-action="undo">${esc(t("undo"))}</button></div>`;
}

const live = document.createElement("div");
live.className = "sr-only";
live.setAttribute("aria-live", "polite");
document.body.append(live);

/** Reads a short message to screen readers without moving focus. */
function announce(text: string) {
  live.textContent = "";
  window.setTimeout(() => (live.textContent = text), 50);
}

async function update(patch: Partial<Settings>) {
  settings = { ...settings, ...patch };
  await saveSettings(settings);
}

async function updateNotify(patch: Partial<Settings["notify"]>) {
  await update({ notify: { ...settings.notify, ...patch } });
}

function regionList(): Array<{ id: string; name: LocalizedName; groups: string[] }> {
  if (data.index?.regions.length) return data.index.regions;
  return REGION_NAMES.map((r) => ({ ...r, groups: DEMO_GROUPS }));
}

function regionName(id: string): string {
  const r = regionList().find((x) => x.id === id);
  return r ? r.name[getLang()] : id;
}

function sourceUrlFor(regionId: string | undefined): string | null {
  if (!regionId) return null;
  const url = data.regions[regionId]?.sourceUrl;
  return url && /^https:\/\//.test(url) ? url : null;
}

function mainPlace(): Place | undefined {
  return settings.places.find((p) => p.id === settings.mainPlaceId) ?? settings.places[0];
}

function toggle(id: string, label: string, checked: boolean): string {
  return `<label class="row toggle-row"><span>${esc(label)}</span>
    <input type="checkbox" class="switch" data-field="${id}" ${checked ? "checked" : ""}/></label>`;
}

function segmented(id: string, title: string, options: Array<[string, string]>, value: string): string {
  return `<span id="lbl-${id}">${esc(title)}</span><div class="segmented" role="radiogroup" aria-labelledby="lbl-${id}">${options
    .map(
      ([v, label]) =>
        `<button role="radio" aria-checked="${v === value}" tabindex="${v === value ? 0 : -1}" class="${v === value ? "active" : ""}" data-seg="${id}" data-value="${v}">${esc(label)}</button>`,
    )
    .join("")}</div>`;
}

function viewIcon(view: string): string {
  if (view === "strip")
    return `<svg viewBox="0 0 80 48"><rect x="4" y="16" width="72" height="16" rx="8" class="vi-card"/><circle cx="14" cy="24" r="3" class="vi-on"/><rect x="22" y="21" width="22" height="3" rx="1.5" class="vi-line"/><rect x="56" y="19" width="14" height="10" rx="2" class="vi-on"/></svg>`;
  if (view === "card")
    return `<svg viewBox="0 0 80 48"><rect x="12" y="4" width="56" height="40" rx="6" class="vi-card"/><rect x="18" y="10" width="20" height="3" rx="1.5" class="vi-line"/><rect x="18" y="19" width="44" height="4" rx="2" class="vi-on"/><rect x="34" y="19" width="10" height="4" class="vi-off"/><rect x="18" y="31" width="44" height="4" rx="2" class="vi-on"/><rect x="46" y="31" width="12" height="4" class="vi-off"/></svg>`;
  return `<svg viewBox="0 0 80 48"><rect x="18" y="2" width="44" height="44" rx="8" class="vi-card"/><circle cx="40" cy="24" r="13" fill="none" stroke-width="5" class="vi-ring"/><path d="M40 11a13 13 0 0 1 12.4 9" fill="none" stroke-width="5" class="vi-ring-off"/></svg>`;
}

function renderAppearance(): string {
  const views: Array<[Settings["view"], StringKey]> = [
    ["strip", "viewStrip"],
    ["card", "viewCard"],
    ["ring", "viewRing"],
  ];
  return `<h1>${esc(t("secAppearance"))}</h1>
  <section class="group">
    <div class="group-title" id="lbl-view">${esc(t("widgetView"))}</div>
    <div class="view-cards" role="radiogroup" aria-labelledby="lbl-view">
      ${views
        .map(
          ([v, k]) => `<button class="view-card ${settings.view === v ? "active" : ""}" role="radio" aria-checked="${settings.view === v}" tabindex="${settings.view === v ? 0 : -1}" data-seg="view" data-value="${v}">
          ${viewIcon(v)}<span>${esc(t(k))}</span></button>`,
        )
        .join("")}
    </div>
    <p class="hint">${esc(t("viewHint"))}</p>
  </section>
  <section class="group">
    <div class="row">${segmented("windowMode", t("placement"), [["top", t("placementTop")], ["desktop", t("placementDesktop")]], settings.windowMode)}</div>
    <div class="row">${segmented("theme", t("theme"), [["system", t("themeSystem")], ["dark", t("themeDark")], ["light", t("themeLight")]], settings.theme)}</div>
    <label class="row"><span>${esc(t("opacity"))}</span><span class="range"><input type="range" min="40" max="100" step="5" value="${settings.opacity}" data-range="opacity"/><output>${settings.opacity}%</output></span></label>
    <label class="row"><span>${esc(t("scale"))}</span><span class="range"><input type="range" min="90" max="125" step="5" value="${settings.scale}" data-range="scale"/><output>${settings.scale}%</output></span></label>
    ${toggle("showTomorrow", t("showTomorrow"), settings.showTomorrow)}
    ${toggle("lockPosition", t("lockPosition"), settings.lockPosition)}
  </section>`;
}

function placeForm(place: Place | null): string {
  const regions = regionList();
  const regionId = place?.regionId ?? "";
  const groups = regions.find((r) => r.id === regionId)?.groups ?? [];
  const help = sourceUrlFor(regionId);
  return `<form class="place-form" data-form="${place ? place.id : "new"}">
    <label class="field"><span>${esc(t("placeName"))}</span>
      <input name="label" maxlength="24" required placeholder="${esc(t("placeNamePlaceholder"))}" value="${esc(place?.label ?? "")}"/></label>
    <label class="field"><span>${esc(t("region"))}</span>
      <select name="regionId" required><option value="">${esc(t("chooseRegion"))}</option>
      ${regions.map((r) => `<option value="${esc(r.id)}" ${r.id === regionId ? "selected" : ""}>${esc(r.name[getLang()])}</option>`).join("")}</select></label>
    <label class="field"><span>${esc(t("group"))}</span>
      <select name="group" required ${groups.length ? "" : "disabled"}><option value="">${esc(t("chooseGroup"))}</option>
      ${groups.map((g) => `<option ${g === place?.group ? "selected" : ""}>${esc(g)}</option>`).join("")}</select></label>
    <div class="form-actions">
      <a href="#" data-link="${esc(help ?? SITE_URL)}" class="link">${esc(t("howToFindGroup"))}</a>
      <span class="spacer"></span>
      ${place ? `<button type="button" class="btn ghost" data-action="cancel-edit">${esc(t("cancel"))}</button>` : ""}
      <button type="submit" class="btn">${esc(place ? t("save") : t("add"))}</button>
    </div>
  </form>`;
}

function renderPlaces(): string {
  const main = mainPlace();
  const list = settings.places
    .map((p) => {
      if (editingId === p.id) return `<li class="place-item editing">${placeForm(p)}</li>`;
      const isMain = p.id === main?.id;
      return `<li class="place-item" draggable="true" data-id="${esc(p.id)}" aria-keyshortcuts="Alt+ArrowUp Alt+ArrowDown">
        <span class="grip" title="${esc(t("dragHint"))}" aria-hidden="true">⋮⋮</span>
        <div class="place-info"><div class="place-name">${esc(p.label)} ${isMain ? `<span class="pill">${esc(t("main"))}</span>` : ""}</div>
          <div class="muted small">${esc(regionName(p.regionId))} · ${esc(t("group"))} ${esc(p.group)}</div></div>
        ${isMain ? "" : `<button class="btn ghost sm" data-action="main" data-id="${esc(p.id)}">${esc(t("makeMain"))}</button>`}
        <button class="btn ghost sm" data-action="edit" data-id="${esc(p.id)}">${esc(t("edit"))}</button>
        <button class="btn ghost sm danger" data-action="remove" data-id="${esc(p.id)}">${esc(t("remove"))}</button>
      </li>`;
    })
    .join("");
  const full = settings.places.length >= MAX_PLACES;
  return `${undoBar()}<h1>${esc(t("secPlaces"))} <span class="counter">${esc(t("placesCount", { n: settings.places.length, max: MAX_PLACES }))}</span></h1>
  ${settings.places.length ? `<ul class="place-list">${list}</ul><p class="hint">${esc(t("dragHint"))}</p>` : ""}
  <section class="group">
    <div class="group-title">${esc(t("addPlace"))}</div>
    ${full ? `<p class="hint">${esc(t("limitReached"))}</p>` : editingId ? "" : placeForm(null)}
  </section>`;
}

function renderNotifications(): string {
  const n = settings.notify;
  return `<h1>${esc(t("secNotifications"))}</h1>
  <section class="group">
    ${toggle("notify.beforeOff", t("beforeOff"), n.beforeOff)}
    ${toggle("notify.beforeOn", t("beforeOn"), n.beforeOn)}
    ${toggle("notify.scheduleChanged", t("scheduleChanged"), n.scheduleChanged)}
    ${toggle("notify.tomorrowPublished", t("tomorrowPublished"), n.tomorrowPublished)}
    ${toggle("notify.sound", t("sound"), n.sound)}
  </section>
  <section class="group">
    <div class="row">${segmented("leadMinutes", t("leadTime"), [5, 10, 15, 30, 60].map((m) => [String(m), t("minutesShort", { n: m })]), String(n.leadMinutes))}</div>
    <div class="row">${segmented("scope", t("forPlaces"), [["main", t("forMain")], ["all", t("forAll")]], n.scope)}</div>
    ${toggle("notify.quietNight", t("quietNight"), n.quietNight)}
  </section>
  <button class="btn ghost" data-action="test-notification">${esc(t("testNotification"))}</button>`;
}

function renderGeneral(): string {
  return `<h1>${esc(t("secGeneral"))}</h1>
  <section class="group">
    <div class="row">${segmented("lang", t("language"), LANGS.map((l) => [l.id, l.label]), settings.lang)}</div>
    <div class="row">${segmented(
      "refreshMinutes",
      t("refreshFreq"),
      [
        ["5", t("minutesShort", { n: 5 })],
        ["10", t("minutesShort", { n: 10 })],
        ["30", t("minutesShort", { n: 30 })],
        ["0", t("manualOnly")],
      ],
      String(settings.refreshMinutes),
    )}</div>
    ${toggle("autostart", t("autostart"), autostartOn)}
    ${toggle("trayIcon", t("trayIcon"), settings.trayIcon)}
  </section>
  <section class="group">
    <div class="row"><span>${esc(t("dataUpdated", { ago: agoText(data.checkedAt) }))}${data.demo ? ` <span class="badge">${esc(t("demo"))}</span>` : ""}</span>
      <button class="btn" data-action="refresh">${esc(t("refreshNow"))}</button></div>
    ${data.demo ? `<p class="hint">${esc(t("demoLong"))}</p>` : ""}
  </section>
  <button class="btn ghost danger" data-action="reset">${esc(t("resetAll"))}</button>`;
}

function renderUpdate(): string {
  const u = updateState;
  let status = "";
  let button = `<button class="btn" data-action="check-update">${esc(t("checkUpdates"))}</button>`;
  if (u.kind === "checking") {
    status = t("checking");
    button = "";
  } else if (u.kind === "latest") status = t("upToDate");
  else if (u.kind === "error")
    status = `${t("updateError")}`;
  else if (u.kind === "available") {
    status = t("updateAvailable", { v: u.update.version });
    button = `<button class="btn" data-action="install-update">${esc(t("installUpdate"))}</button>`;
  } else if (u.kind === "installing") {
    status = t("installing", { p: u.progress });
    button = "";
  }
  const releases = u.kind === "error" ? ` <a href="#" class="link" data-link="${PROJECT_URL}/releases">${esc(t("releasesPage"))}</a>` : "";
  return `<div class="update-row">${button}<span class="muted">${esc(status)}</span>${releases}</div>`;
}

async function checkForUpdate() {
  updateState = { kind: "checking" };
  render();
  try {
    const u = await findUpdate();
    updateState = u ? { kind: "available", update: u } : { kind: "latest" };
  } catch (e) {
    console.warn(e);
    updateState = { kind: "error" };
  }
  render();
  const st = updateState as UpdateState;
  announce(st.kind === "available" ? t("updateAvailable", { v: st.update.version }) : st.kind === "latest" ? t("upToDate") : t("updateError"));
}

async function installUpdate() {
  if (updateState.kind !== "available") return;
  const update = updateState.update;
  updateState = { kind: "installing", progress: "" };
  render();
  try {
    await installAndRestart(update, (done, total) => {
      updateState = { kind: "installing", progress: total ? `${Math.round((done / total) * 100)}%` : "" };
      render();
    });
  } catch (e) {
    console.warn(e);
    updateState = { kind: "error" };
    render();
  }
}

function renderAbout(): string {
  const src = sourceUrlFor(mainPlace()?.regionId);
  const links: Array<[string, string | null]> = [
    [t("dataSource"), src],
    [t("website"), SITE_URL],
    [t("privacy"), PRIVACY_URL],
    [t("reportError"), `${PROJECT_URL}/issues/new`],
  ];
  return `<div class="about-head">${logoMark(64)}<div><div class="about-name">Коли <em>Є</em></div>
    <div class="muted">${esc(t("version", { v: APP_VERSION }))} · ${esc(t("author"))}</div></div></div>
  ${renderUpdate()}
  <section class="group"><p class="disclaimer">${esc(t("disclaimer"))}</p></section>
  <section class="group links">${links
    .filter(([, url]) => url)
    .map(([label, url]) => `<a href="#" class="row link-row" data-link="${esc(url!)}"><span>${esc(label)}</span><span aria-hidden="true">↗</span></a>`)
    .join("")}</section>`;
}

const renderers: Record<Section, () => string> = {
  appearance: renderAppearance,
  places: renderPlaces,
  notifications: renderNotifications,
  general: renderGeneral,
  about: renderAbout,
};

/** A selector that finds the same control again after the page is re-rendered. */
function focusSelector(el: HTMLElement | null): string | null {
  if (!el || el === document.body || !app.contains(el)) return null;
  const q = (attr: string, v: string | undefined) => (v != null ? `[${attr}="${CSS.escape(v)}"]` : "");
  const form = el.closest<HTMLElement>("[data-form]");
  const scope = form ? `[data-form="${CSS.escape(form.dataset.form!)}"] ` : "";
  const name = el.getAttribute("name");
  if (name) return `${scope}[name="${CSS.escape(name)}"]`;
  const d = el.dataset;
  if (d.section) return q("data-section", d.section);
  if (d.seg) return q("data-seg", d.seg) + q("data-value", d.value);
  if (d.field) return q("data-field", d.field);
  if (d.range) return q("data-range", d.range);
  if (d.action) return scope + q("data-action", d.action) + q("data-id", d.id);
  if (d.link) return scope + q("data-link", d.link);
  return ".content h1";
}

function render() {
  const sel = focusSelector(document.activeElement as HTMLElement | null);
  document.title = `${t("appName")} — ${t("traySettings")}`;
  app.innerHTML = `<nav class="side">
      <div class="side-brand">${logoMark(26)}<span>Коли <em>Є</em></span></div>
      ${SECTIONS.map(
        (s) => `<button class="nav-item ${s.id === section ? "active" : ""}" data-section="${s.id}" aria-current="${s.id === section ? "page" : "false"}">
          <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true"><path d="${s.icon}" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"/></svg>
          ${esc(t(s.key))}</button>`,
      ).join("")}
    </nav>
    <main class="content">${renderers[section]().replace("<h1>", '<h1 tabindex="-1">')}</main>`;
  if (sel) {
    // A control that is gone (e.g. a removed place): keep focus in the page instead of dropping it to <body>.
    const order = [sel, '[data-action="undo"]', ".content h1", ".nav-item.active"];
    order.map((q) => app.querySelector<HTMLElement>(q)).find(Boolean)?.focus();
  }
}

/** True while the user is in the middle of an input that a re-render would reset. */
function busy(): boolean {
  const a = document.activeElement;
  if (a instanceof HTMLInputElement && a.type === "range") return true;
  return !!a?.closest(".place-form");
}

function newId(): string {
  return Math.random().toString(36).slice(2, 10);
}

function wire() {
  app.addEventListener("click", async (e) => {
    const el = e.target as HTMLElement;
    const nav = el.closest<HTMLElement>("[data-section]");
    if (nav) {
      section = nav.dataset.section as Section;
      editingId = null;
      return render();
    }
    const link = el.closest<HTMLElement>("[data-link]");
    if (link) {
      e.preventDefault();
      return void openUrl(link.dataset.link!);
    }
    const seg = el.closest<HTMLElement>("[data-seg]");
    if (seg) {
      const field = seg.dataset.seg!;
      const value = seg.dataset.value!;
      if (field === "leadMinutes") return updateNotify({ leadMinutes: Number(value) });
      if (field === "scope") return updateNotify({ scope: value as "main" | "all" });
      if (field === "refreshMinutes") return update({ refreshMinutes: Number(value) });
      return update({ [field]: value } as Partial<Settings>);
    }
    const action = el.closest<HTMLElement>("[data-action]");
    if (!action) return;
    const id = action.dataset.id;
    switch (action.dataset.action) {
      case "main":
        return update({ mainPlaceId: id ?? null });
      case "edit":
        editingId = id ?? null;
        return render();
      case "cancel-edit":
        editingId = null;
        return render();
      case "remove": {
        const removed = settings.places.find((p) => p.id === id);
        if (!removed) return;
        const places = settings.places.filter((p) => p.id !== id);
        const mainPlaceId = settings.mainPlaceId === id ? (places[0]?.id ?? null) : settings.mainPlaceId;
        setUndo({ label: removed.label, places: settings.places, mainPlaceId: settings.mainPlaceId });
        announce(t("placeRemoved", { name: removed.label }));
        return update({ places, mainPlaceId });
      }
      case "undo": {
        const u = undo;
        if (!u) return;
        setUndo(null);
        return update({ places: u.places, mainPlaceId: u.mainPlaceId });
      }
      case "check-update":
        return checkForUpdate();
      case "install-update":
        return installUpdate();
      case "test-notification":
        return sendTest(settings);
      case "refresh":
        return emit("refresh-request");
      case "reset":
        if (!confirm(t("resetConfirm"))) return;
        if (autostartOn) await disableAutostart().catch(() => undefined);
        autostartOn = false;
        return update(defaultSettings());
    }
  });

  app.addEventListener("change", async (e) => {
    const el = e.target as HTMLInputElement | HTMLSelectElement;
    const field = el.dataset.field;
    if (field && el instanceof HTMLInputElement && el.type === "checkbox") {
      if (field.startsWith("notify.")) return updateNotify({ [field.slice(7)]: el.checked });
      if (field === "autostart") {
        try {
          if (el.checked) await enableAutostart();
          else await disableAutostart();
          autostartOn = await autostartEnabled();
        } catch (err) {
          console.error(err);
        }
        return update({ autostart: autostartOn });
      }
      return update({ [field]: el.checked } as Partial<Settings>);
    }
    if (el.name === "regionId") {
      // Refill groups for the chosen region.
      const form = el.closest("form")!;
      const groupSel = form.querySelector<HTMLSelectElement>('select[name="group"]')!;
      const groups = regionList().find((r) => r.id === el.value)?.groups ?? [];
      groupSel.innerHTML = `<option value="">${esc(t("chooseGroup"))}</option>${groups.map((g) => `<option>${esc(g)}</option>`).join("")}`;
      groupSel.disabled = groups.length === 0;
      const help = form.querySelector<HTMLElement>("[data-link]");
      if (help) help.dataset.link = sourceUrlFor(el.value) ?? SITE_URL;
    }
  });

  app.addEventListener("input", (e) => {
    const el = e.target as HTMLInputElement;
    const key = el.dataset.range as "opacity" | "scale" | undefined;
    if (!key) return;
    const out = el.parentElement?.querySelector("output");
    if (out) out.textContent = `${el.value}%`;
    void update({ [key]: Number(el.value) } as Partial<Settings>);
  });

  app.addEventListener("submit", (e) => {
    e.preventDefault();
    const form = e.target as HTMLFormElement;
    const fd = new FormData(form);
    const label = String(fd.get("label") ?? "").trim();
    const regionId = String(fd.get("regionId") ?? "");
    const group = String(fd.get("group") ?? "");
    if (!label || !regionId || !group) return;
    const target = form.dataset.form!;
    (document.activeElement as HTMLElement | null)?.blur();
    if (target === "new") {
      if (settings.places.length >= MAX_PLACES) return;
      const place: Place = { id: newId(), label, regionId, group };
      editingId = null;
      void update({ places: [...settings.places, place], mainPlaceId: settings.mainPlaceId ?? place.id });
    } else {
      editingId = null;
      void update({ places: settings.places.map((p) => (p.id === target ? { ...p, label, regionId, group } : p)) });
    }
  });

  app.addEventListener("keydown", (e) => {
    const el = e.target as HTMLElement;
    const radio = el.closest<HTMLElement>('[role="radio"]');
    if (radio && ["ArrowLeft", "ArrowRight", "ArrowUp", "ArrowDown", "Home", "End"].includes(e.key)) {
      const items = [...radio.parentElement!.querySelectorAll<HTMLElement>('[role="radio"]')];
      const i = items.indexOf(radio);
      const last = items.length - 1;
      const back = e.key === "ArrowLeft" || e.key === "ArrowUp";
      const next = e.key === "Home" ? 0 : e.key === "End" ? last : back ? (i === 0 ? last : i - 1) : i === last ? 0 : i + 1;
      e.preventDefault();
      items[next].focus();
      items[next].click();
      return;
    }
    const item = el.closest<HTMLElement>(".place-item[data-id]");
    if (item && e.altKey && (e.key === "ArrowUp" || e.key === "ArrowDown")) {
      e.preventDefault();
      const places = [...settings.places];
      const i = places.findIndex((p) => p.id === item.dataset.id);
      const j = e.key === "ArrowUp" ? i - 1 : i + 1;
      if (i < 0 || j < 0 || j >= places.length) return;
      [places[i], places[j]] = [places[j], places[i]];
      announce(t("movedTo", { name: places[j].label, n: j + 1, total: places.length }));
      void update({ places });
    }
  });

  // Drag-and-drop reordering of places.
  let dragId: string | null = null;
  app.addEventListener("dragstart", (e) => {
    const item = (e.target as HTMLElement).closest<HTMLElement>(".place-item");
    dragId = item?.dataset.id ?? null;
    item?.classList.add("dragging");
    e.dataTransfer?.setData("text/plain", dragId ?? "");
  });
  app.addEventListener("dragover", (e) => {
    if (!dragId) return;
    const item = (e.target as HTMLElement).closest<HTMLElement>(".place-item");
    if (!item || item.dataset.id === dragId) return;
    e.preventDefault();
    const list = item.parentElement!;
    const dragging = list.querySelector<HTMLElement>(".dragging");
    if (!dragging) return;
    const after = e.clientY > item.getBoundingClientRect().top + item.offsetHeight / 2;
    list.insertBefore(dragging, after ? item.nextSibling : item);
  });
  app.addEventListener("dragend", () => {
    if (!dragId) return;
    dragId = null;
    const order = [...app.querySelectorAll<HTMLElement>(".place-item[data-id]")].map((el) => el.dataset.id!);
    const places = order.map((id) => settings.places.find((p) => p.id === id)!).filter(Boolean);
    void update({ places });
  });
}

async function main() {
  settings = await loadSettings();
  data = await loadCachedState();
  setLang(settings.lang);
  applyTheme(settings, false);
  try {
    autostartOn = await autostartEnabled();
  } catch {
    autostartOn = false;
  }
  if (!settings.places.length && !new URLSearchParams(location.search).get("section")) section = "places";
  wire();
  render();
  await onSettingsChanged((s) => {
    settings = s;
    setLang(s.lang);
    applyTheme(s, false);
    if (!busy()) render();
  });
  await listen<DataState>("data-updated", (e) => {
    data = e.payload;
    if (!busy()) render();
  });
  onSystemThemeChange(() => {
    applyTheme(settings, false);
  });
  await listen<string>("settings-section", (e) => {
    section = e.payload as Section;
    editingId = null;
    render();
    if (section === "about" && updateState.kind === "idle") void checkForUpdate();
  });
  if (section === "about") void checkForUpdate();
  void emit("data-request");
  window.setInterval(() => {
    if (section === "general") render();
  }, 30_000);
}

void main();
