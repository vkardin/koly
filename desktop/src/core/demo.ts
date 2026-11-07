import { addDays, formatHm, kyivNow } from "./schedule";
import { DEMO_GROUPS, REGION_NAMES } from "./regions";
import type { DaySchedule, IndexFile, Interval, RegionFile } from "./types";

function hash(s: string): number {
  let h = 2166136261;
  for (let i = 0; i < s.length; i++) {
    h ^= s.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return h >>> 0;
}

function hm(minutes: number): string {
  return minutes >= 1440 ? "24:00" : formatHm(minutes);
}

/** Deterministic sample schedule: rotating 4-hour blocks with occasional "maybe" tails. */
function demoIntervals(regionId: string, group: string, date: string): Interval[] {
  const seed = hash(`${regionId}|${date}`);
  const gi = DEMO_GROUPS.indexOf(group);
  const queue = Math.floor(Math.max(0, gi) / 2);
  const shift = (seed % 6 + queue) % 6;
  const out: Interval[] = [];
  for (let block = 0; block < 6; block++) {
    if ((block + shift) % 3 !== 0) continue;
    const start = block * 240 + (gi % 2) * 30;
    const end = Math.min(1440, start + 210);
    out.push({ from: hm(start), to: hm(end), type: "off" });
    if ((seed >> block) & 1 && end < 1440) out.push({ from: hm(end), to: hm(Math.min(1440, end + 60)), type: "maybe" });
  }
  return out;
}

export function demoIndex(): IndexFile {
  const now = new Date().toISOString();
  return {
    schemaVersion: 1,
    generatedAt: now,
    regions: REGION_NAMES.map((r) => ({
      id: r.id,
      name: r.name,
      provider: "demo",
      groups: DEMO_GROUPS,
      updatedAt: now,
      status: "ok",
    })),
  };
}

export function demoRegion(regionId: string): RegionFile {
  const today = kyivNow().date;
  const days: DaySchedule[] = [today, addDays(today, 1)].map((date) => ({
    date,
    groups: Object.fromEntries(DEMO_GROUPS.map((g) => [g, demoIntervals(regionId, g, date)])),
  }));
  const now = new Date().toISOString();
  return {
    id: regionId,
    provider: "demo",
    sourceUrl: "",
    fetchedAt: now,
    updatedAt: now,
    status: "ok",
    emergency: { active: false, note: null, since: null },
    days,
  };
}
