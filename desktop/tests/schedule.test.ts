import assert from "node:assert/strict";
import { test } from "node:test";
import { buildSegments, formatCountdown, kyivNow, placeStatus, type KyivNow } from "../src/core/schedule";
import type { RegionFile } from "../src/core/types";

function region(days: RegionFile["days"]): RegionFile {
  return {
    id: "r",
    provider: "p",
    sourceUrl: "https://example.org",
    fetchedAt: "",
    updatedAt: "",
    status: "ok",
    emergency: { active: false, note: null, since: null },
    days,
  };
}

const at = (date: string, tomorrow: string, hh: number, mm: number): KyivNow => ({ date, tomorrow, minutes: hh * 60 + mm });

test("segments cover the whole day and off wins over maybe", () => {
  const segs = buildSegments([
    { from: "08:00", to: "12:00", type: "maybe" },
    { from: "10:00", to: "11:00", type: "off" },
    { from: "22:00", to: "24:00", type: "off" },
  ]);
  assert.deepEqual(
    segs.map((s) => [s.start, s.end, s.state]),
    [
      [0, 480, "on"],
      [480, 600, "maybe"],
      [600, 660, "off"],
      [660, 720, "maybe"],
      [720, 1320, "on"],
      [1320, 1440, "off"],
    ],
  );
});

test("no intervals means power on all day", () => {
  assert.deepEqual(buildSegments([]), [{ start: 0, end: 1440, state: "on" }]);
});

test("countdown to the next change today", () => {
  const r = region([{ date: "2026-11-07", groups: { "1.1": [{ from: "18:00", to: "22:00", type: "off" }] } }]);
  const ps = placeStatus(r, "1.1", at("2026-11-07", "2026-11-08", 15, 46));
  assert.equal(ps.state, "on");
  assert.equal(formatCountdown(ps.untilChange!), "2:14");
  assert.equal(ps.changeAt, 1080);
  assert.equal(ps.nextState, "off");
  assert.equal(ps.nextStateEnd, 1320);
});

test("outage that continues past midnight is merged across days", () => {
  const r = region([
    { date: "2026-11-07", groups: { g: [{ from: "22:00", to: "24:00", type: "off" }] } },
    { date: "2026-11-08", groups: { g: [{ from: "00:00", to: "02:00", type: "off" }] } },
  ]);
  const ps = placeStatus(r, "g", at("2026-11-07", "2026-11-08", 23, 0));
  assert.equal(ps.state, "off");
  assert.equal(ps.untilChange, 180);
  assert.equal(ps.changeAt, 1440 + 120);
  assert.equal(ps.nextState, "on");
});

test("without tomorrow's schedule there is no change after the last event", () => {
  const r = region([{ date: "2026-11-07", groups: { g: [{ from: "08:00", to: "10:00", type: "off" }] } }]);
  const ps = placeStatus(r, "g", at("2026-11-07", "2026-11-08", 12, 0));
  assert.equal(ps.state, "on");
  assert.equal(ps.untilChange, null);
  assert.equal(ps.hasTomorrow, false);
});

test("next day becomes today after midnight", () => {
  const r = region([
    { date: "2026-11-07", groups: { g: [] } },
    { date: "2026-11-08", groups: { g: [{ from: "00:00", to: "04:00", type: "off" }] } },
  ]);
  const ps = placeStatus(r, "g", at("2026-11-08", "2026-11-09", 0, 30));
  assert.equal(ps.hasToday, true);
  assert.equal(ps.state, "off");
  assert.equal(ps.untilChange, 210);
});

test("missing day is reported as missing, not as power on", () => {
  const r = region([{ date: "2026-11-06", groups: { g: [] } }]);
  const ps = placeStatus(r, "g", at("2026-11-07", "2026-11-08", 9, 0));
  assert.equal(ps.hasToday, false);
});

test("Kyiv time is used regardless of the system time zone", () => {
  // 2026-11-07 22:30 UTC is 00:30 on 2026-11-08 in Kyiv (UTC+2 in winter).
  const n = kyivNow(new Date("2026-11-07T22:30:00Z"));
  assert.equal(n.date, "2026-11-08");
  assert.equal(n.tomorrow, "2026-11-09");
  assert.equal(Math.round(n.minutes), 30);
});

test("daylight saving switch keeps the Kyiv wall clock", () => {
  // Summer time: UTC+3. 2026-07-01 21:15 UTC is 00:15 on 2026-07-02.
  const summer = kyivNow(new Date("2026-07-01T21:15:00Z"));
  assert.equal(summer.date, "2026-07-02");
  assert.equal(Math.round(summer.minutes), 15);
  // Clocks go back on 2026-10-25 at 04:00 local; 01:30 UTC is 03:30 local (UTC+2).
  const after = kyivNow(new Date("2026-10-25T01:30:00Z"));
  assert.equal(after.date, "2026-10-25");
  assert.equal(Math.round(after.minutes), 210);
});

test("countdown rounds up partial minutes and never goes negative", () => {
  assert.equal(formatCountdown(0.2), "0:01");
  assert.equal(formatCountdown(-5), "0:00");
  assert.equal(formatCountdown(125), "2:05");
});
