import assert from "node:assert/strict";
import { test } from "node:test";
import { emptyState, refresh } from "../src/core/data";

const index = {
  schemaVersion: 1,
  generatedAt: "2026-11-07T12:00:00+02:00",
  regions: [{ id: "kyiv-city", name: { uk: "Київ", en: "Kyiv", ru: "Киев" }, provider: "p", groups: ["1.1"], updatedAt: "", status: "ok" }],
};
const regionFile = { id: "kyiv-city", provider: "p", sourceUrl: "https://x", fetchedAt: "", updatedAt: "", status: "ok", emergency: { active: false, note: null, since: null }, days: [] };

function mockFetch(handler: (url: string) => Response | Promise<Response>) {
  globalThis.fetch = (async (input: RequestInfo | URL) => handler(String(input))) as typeof fetch;
}
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

test("first refresh without network falls back to labelled sample data", async () => {
  mockFetch(() => {
    throw new TypeError("offline");
  });
  const s = await refresh(emptyState(), ["kyiv-city"]);
  assert.equal(s.demo, true);
  assert.equal(s.offline, true);
  assert.ok(s.regions["kyiv-city"].days.length > 0);
});

test("real data replaces sample data", async () => {
  mockFetch((url) => json(url.endsWith("index.json") ? index : regionFile));
  const demo = { ...emptyState(), demo: true, regions: { lviv: regionFile as never } };
  const s = await refresh(demo, ["kyiv-city"]);
  assert.equal(s.demo, false);
  assert.equal(s.offline, false);
  assert.deepEqual(Object.keys(s.regions), ["kyiv-city"]);
});

test("going offline keeps the last real data", async () => {
  mockFetch((url) => json(url.endsWith("index.json") ? index : regionFile));
  const good = await refresh(emptyState(), ["kyiv-city"]);
  mockFetch(() => {
    throw new TypeError("offline");
  });
  const s = await refresh(good, ["kyiv-city"]);
  assert.equal(s.demo, false);
  assert.equal(s.offline, true);
  assert.equal(s.regions["kyiv-city"].sourceUrl, "https://x");
  assert.equal(s.checkedAt, good.checkedAt);
});

test("broken or empty responses never overwrite valid data", async () => {
  mockFetch((url) => json(url.endsWith("index.json") ? index : regionFile));
  const good = await refresh(emptyState(), ["kyiv-city"]);
  mockFetch((url) => json(url.endsWith("index.json") ? { schemaVersion: 1, regions: [] } : {}));
  const s = await refresh(good, ["kyiv-city"]);
  assert.equal(s.index?.regions.length, 1);
  assert.equal(s.regions["kyiv-city"].id, "kyiv-city");
  mockFetch((url) => (url.endsWith("index.json") ? json(index) : json({ id: "other", days: [] })));
  const s2 = await refresh(good, ["kyiv-city"]);
  assert.equal(s2.regions["kyiv-city"].sourceUrl, "https://x");
});
