import { isPermissionGranted, requestPermission, sendNotification } from "@tauri-apps/plugin-notification";
import type { DataState } from "./data";
import { t } from "./i18n";
import { formatHm, kyivNow, placeStatus, scheduleSignature } from "./schedule";
import { readJson, writeJson } from "./store";
import type { Place, Settings } from "./types";

const STATE_FILE = "notify-state.json";

interface NotifyState {
  sent: string[];
  signatures: Record<string, string>;
  tomorrowSeen: Record<string, boolean>;
}

let state: NotifyState = { sent: [], signatures: {}, tomorrowSeen: {} };
let loaded = false;

async function ensureLoaded() {
  if (loaded) return;
  state = (await readJson<NotifyState>(STATE_FILE)) ?? state;
  loaded = true;
}

function persist() {
  state.sent = state.sent.slice(-200);
  void writeJson(STATE_FILE, state);
}

function quietNow(s: Settings): boolean {
  if (!s.notify.quietNight) return false;
  const h = new Date().getHours();
  return h >= 23 || h < 7;
}

async function permitted(): Promise<boolean> {
  try {
    if (await isPermissionGranted()) return true;
    return (await requestPermission()) === "granted";
  } catch {
    return false;
  }
}

async function show(s: Settings, title: string, body: string) {
  if (!(await permitted())) return;
  sendNotification({ title, body, sound: s.notify.sound ? "Default" : undefined });
}

export async function sendTest(s: Settings) {
  await show(s, t("nTestTitle"), t("nTestBody"));
}

function placesInScope(s: Settings): Place[] {
  if (s.notify.scope === "all") return s.places;
  const main = s.places.find((p) => p.id === s.mainPlaceId) ?? s.places[0];
  return main ? [main] : [];
}

/** Called every tick: warnings before outages and before power returns. */
export async function checkUpcoming(s: Settings, data: DataState) {
  if (!s.notify.beforeOff && !s.notify.beforeOn) return;
  await ensureLoaded();
  const now = kyivNow();
  const lead = s.notify.leadMinutes;
  for (const place of placesInScope(s)) {
    const region = data.regions[place.regionId];
    if (!region || region.status === "no_schedules") continue;
    const ps = placeStatus(region, place.group, now);
    if (ps.untilChange == null || ps.changeAt == null || ps.untilChange <= 0 || ps.untilChange > lead) continue;
    const goingOff = ps.state === "on" && ps.nextState !== "on";
    const comingOn = ps.state !== "on" && ps.nextState === "on";
    if ((goingOff && !s.notify.beforeOff) || (comingOn && !s.notify.beforeOn) || (!goingOff && !comingOn)) continue;
    const key = `${place.id}|${now.date}|${ps.changeAt}|${goingOff ? "off" : "on"}`;
    if (state.sent.includes(key)) continue;
    state.sent.push(key);
    persist();
    if (quietNow(s)) continue;
    const n = Math.ceil(ps.untilChange);
    if (goingOff) {
      const to = ps.nextStateEnd != null ? formatHm(ps.nextStateEnd) : "—";
      await show(s, t("nOffSoonTitle", { place: place.label, n }), t("nOffSoonBody", { from: formatHm(ps.changeAt), to }));
    } else {
      await show(s, t("nOnSoonTitle", { place: place.label, n }), t("nOnSoonBody", { time: formatHm(ps.changeAt) }));
    }
  }
}

/** Called after each data refresh: schedule changes and tomorrow's publication. */
export async function checkAfterRefresh(s: Settings, data: DataState) {
  if (data.demo) return;
  await ensureLoaded();
  const now = kyivNow();
  let changed = false;
  for (const place of s.places) {
    const region = data.regions[place.regionId];
    if (!region) continue;
    const inScope = placesInScope(s).some((p) => p.id === place.id);
    const sigKey = `${place.id}|${place.regionId}|${place.group}|${now.date}`;
    const sig = scheduleSignature(region, place.group, now.date);
    const prev = state.signatures[sigKey];
    if (sig && prev !== sig) {
      state.signatures[sigKey] = sig;
      changed = true;
      if (prev !== undefined && inScope && s.notify.scheduleChanged && !quietNow(s)) {
        await show(s, t("nChangedTitle", { place: place.label }), t("nChangedBody"));
      }
    }
    const tomorrowKey = `${place.id}|${place.regionId}|${place.group}|${now.tomorrow}`;
    const hasTomorrow = !!region.days.find((d) => d.date === now.tomorrow);
    if (hasTomorrow && !state.tomorrowSeen[tomorrowKey]) {
      state.tomorrowSeen[tomorrowKey] = true;
      changed = true;
      if (inScope && s.notify.tomorrowPublished && !quietNow(s)) {
        const ps = placeStatus(region, place.group, now);
        await show(s, t("nTomorrowTitle", { place: place.label }), t("nTomorrowBody", { n: ps.offCountTomorrow }));
      }
    }
  }
  if (changed) {
    // Keep only recent keys.
    const keep = (k: string) => k.endsWith(now.date) || k.endsWith(now.tomorrow);
    state.signatures = Object.fromEntries(Object.entries(state.signatures).filter(([k]) => keep(k)));
    state.tomorrowSeen = Object.fromEntries(Object.entries(state.tomorrowSeen).filter(([k]) => keep(k)));
    persist();
  }
}
