import type { DaySchedule, Interval, RegionFile } from "./types";

export type SegmentState = "on" | "off" | "maybe";

export interface Segment {
  start: number; // minutes from local midnight, inclusive
  end: number; // exclusive, up to 1440
  state: SegmentState;
}

export const DAY_MINUTES = 1440;
const KYIV_TZ = "Europe/Kyiv";

const kyivParts = new Intl.DateTimeFormat("en-CA", {
  timeZone: KYIV_TZ,
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
  hourCycle: "h23",
});

export interface KyivNow {
  date: string; // YYYY-MM-DD
  tomorrow: string;
  minutes: number; // fractional minutes since midnight
}

export function kyivNow(at: Date = new Date()): KyivNow {
  const p: Record<string, string> = {};
  for (const part of kyivParts.formatToParts(at)) p[part.type] = part.value;
  const date = `${p.year}-${p.month}-${p.day}`;
  const minutes = Number(p.hour) * 60 + Number(p.minute) + Number(p.second) / 60;
  return { date, tomorrow: addDays(date, 1), minutes };
}

export function addDays(date: string, days: number): string {
  const [y, m, d] = date.split("-").map(Number);
  const t = new Date(Date.UTC(y, m - 1, d + days));
  return t.toISOString().slice(0, 10);
}

export function parseHm(value: string): number {
  const [h, m] = value.split(":").map(Number);
  return Math.min(DAY_MINUTES, Math.max(0, h * 60 + (m || 0)));
}

export function formatHm(minutes: number): string {
  const total = Math.round(minutes) % DAY_MINUTES;
  const h = Math.floor(total / 60);
  const m = total % 60;
  return `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}`;
}

/** Countdown text like "2:14" (hours:minutes), rounded up to the next minute. */
export function formatCountdown(minutes: number): string {
  const total = Math.max(0, Math.ceil(minutes - 1e-6));
  const h = Math.floor(total / 60);
  const m = total % 60;
  return `${h}:${String(m).padStart(2, "0")}`;
}

/** Builds a full-day timeline; "off" wins over "maybe" where they overlap. */
export function buildSegments(intervals: Interval[] | undefined): Segment[] {
  const states = new Array<SegmentState>(DAY_MINUTES).fill("on");
  for (const it of intervals ?? []) {
    const from = parseHm(it.from);
    const to = parseHm(it.to);
    for (let i = from; i < to; i++) {
      if (it.type === "off" || states[i] === "on") states[i] = it.type;
    }
  }
  const out: Segment[] = [];
  let start = 0;
  for (let i = 1; i <= DAY_MINUTES; i++) {
    if (i === DAY_MINUTES || states[i] !== states[start]) {
      out.push({ start, end: i, state: states[start] });
      start = i;
    }
  }
  return out;
}

export function findDay(region: RegionFile | undefined, date: string): DaySchedule | undefined {
  return region?.days.find((d) => d.date === date);
}

export interface PlaceStatus {
  hasToday: boolean;
  hasTomorrow: boolean;
  today: Segment[];
  tomorrow: Segment[] | null;
  state: SegmentState;
  /** Minutes until the state changes; null if no change is known. */
  untilChange: number | null;
  /** Kyiv minutes of the change (may be >= 1440 for tomorrow). */
  changeAt: number | null;
  nextState: SegmentState | null;
  /** End of the next state, used for "до 22:00 без світла". */
  nextStateEnd: number | null;
  offMinutesToday: number;
  offCountToday: number;
  offCountTomorrow: number;
}

export function placeStatus(region: RegionFile | undefined, group: string, now: KyivNow): PlaceStatus {
  const todayDay = findDay(region, now.date);
  const tomorrowDay = findDay(region, now.tomorrow);
  const today = buildSegments(todayDay?.groups[group]);
  const tomorrow = tomorrowDay ? buildSegments(tomorrowDay.groups[group]) : null;

  // Concatenate today + tomorrow (if known) into one timeline in minutes from today's midnight.
  const timeline: Segment[] = today.map((s) => ({ ...s }));
  if (tomorrow) {
    for (const s of tomorrow) {
      const last = timeline[timeline.length - 1];
      if (last && s.start === 0 && last.end === DAY_MINUTES && last.state === s.state) {
        last.end = DAY_MINUTES + s.end;
      } else {
        timeline.push({ start: DAY_MINUTES + s.start, end: DAY_MINUTES + s.end, state: s.state });
      }
    }
  }
  const horizon = tomorrow ? DAY_MINUTES * 2 : DAY_MINUTES;

  const idx = timeline.findIndex((s) => now.minutes >= s.start && now.minutes < s.end);
  const cur = timeline[Math.max(0, idx)];
  let untilChange: number | null = null;
  let changeAt: number | null = null;
  let nextState: SegmentState | null = null;
  let nextStateEnd: number | null = null;
  if (cur && cur.end < horizon) {
    changeAt = cur.end;
    untilChange = cur.end - now.minutes;
    const next = timeline[idx + 1];
    if (next) {
      nextState = next.state;
      nextStateEnd = next.end < horizon ? next.end : null;
    }
  }

  const offMinutesToday = today.filter((s) => s.state === "off").reduce((a, s) => a + s.end - s.start, 0);
  return {
    hasToday: !!todayDay,
    hasTomorrow: !!tomorrowDay,
    today,
    tomorrow,
    state: cur?.state ?? "on",
    untilChange,
    changeAt,
    nextState,
    nextStateEnd,
    offMinutesToday,
    offCountToday: today.filter((s) => s.state !== "on").length,
    offCountTomorrow: tomorrow ? tomorrow.filter((s) => s.state !== "on").length : 0,
  };
}

/** Moments where the state changes during the day (excluding midnight). */
export function changePoints(segments: Segment[]): number[] {
  return segments.slice(1).map((s) => s.start);
}

/** Stable signature of a group's schedule for change detection. */
export function scheduleSignature(region: RegionFile | undefined, group: string, date: string): string {
  const day = findDay(region, date);
  if (!day) return "";
  return JSON.stringify(day.groups[group] ?? []);
}
