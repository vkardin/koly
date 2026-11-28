import { DAY_MINUTES, buildSegments, findDay } from "./schedule";
import type { RegionFile } from "./types";

export type RowTime = "past" | "now" | "next" | "later";

export interface DetailRow {
  start: number;
  end: number;
  state: "off" | "maybe";
  time: RowTime;
}

export interface DayDetails {
  date: string;
  rows: DetailRow[];
  offMinutes: number;
  maybeMinutes: number;
  onMinutes: number;
  /** The longest stretch with power in the day, if there is any outage to be between. */
  longestOn: { start: number; end: number } | null;
}

/**
 * Everything the "Detailed" view prints about one day of one group: each outage with its
 * length and whether it is over, going on or next, and the day's totals.
 * `nowMinutes` is null for a day that has not started (tomorrow).
 */
export function dayDetails(region: RegionFile | undefined, group: string, date: string, nowMinutes: number | null): DayDetails | null {
  const day = findDay(region, date);
  if (!day) return null;
  const segments = buildSegments(day.groups[group]);
  let nextMarked = false;
  const rows: DetailRow[] = [];
  for (const s of segments) {
    if (s.state === "on") continue;
    let time: RowTime = "later";
    if (nowMinutes != null) {
      if (s.end <= nowMinutes) time = "past";
      else if (s.start <= nowMinutes) time = "now";
      else if (!nextMarked) {
        time = "next";
        nextMarked = true;
      }
    }
    rows.push({ start: s.start, end: s.end, state: s.state, time });
  }
  const sum = (state: string) => segments.filter((s) => s.state === state).reduce((a, s) => a + s.end - s.start, 0);
  const on = segments.filter((s) => s.state === "on");
  const longest = on.reduce<{ start: number; end: number } | null>((best, s) => (!best || s.end - s.start > best.end - best.start ? s : best), null);
  return {
    date,
    rows,
    offMinutes: sum("off"),
    maybeMinutes: sum("maybe"),
    onMinutes: sum("on"),
    longestOn: rows.length && longest ? { start: longest.start, end: longest.end } : null,
  };
}

export interface SourceSummary {
  host: string;
  updatedAt: string | null;
  checks: Array<{ name: string; result: string }>;
  agree: number;
}

/** Where the schedule comes from and how the other sources compare (see `checks` in the region file). */
export function sourceSummary(region: RegionFile | undefined): SourceSummary | null {
  if (!region) return null;
  let host = "";
  try {
    host = new URL(region.sourceUrl).hostname.replace(/^www\./, "");
  } catch {
    host = "";
  }
  const checks = (region.checks ?? []).map((c) => ({ name: c.name, result: c.result }));
  return {
    host,
    updatedAt: region.updatedAt && region.updatedAt.length >= 16 ? region.updatedAt.slice(11, 16) : null,
    checks,
    agree: checks.filter((c) => c.result === "same").length,
  };
}

export { DAY_MINUTES };
