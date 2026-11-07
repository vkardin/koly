import { demoIndex, demoRegion } from "./demo";
import { readJson, writeJson } from "./store";
import type { IndexFile, RegionFile } from "./types";

export const DATA_BASE_URL = "https://vkardin.github.io/koly/v1/";
export const APP_VERSION = "1.0.0";
const CACHE_FILE = "data-cache.json";
const TIMEOUT_MS = 15000;

export interface DataState {
  index: IndexFile | null;
  regions: Record<string, RegionFile>;
  etags: Record<string, string>;
  /** Epoch ms of the last successful check against the server. */
  checkedAt: number | null;
  /** True when no real data has ever been received and sample data is shown. */
  demo: boolean;
  /** True when the last refresh attempt failed. */
  offline: boolean;
}

export function emptyState(): DataState {
  return { index: null, regions: {}, etags: {}, checkedAt: null, demo: false, offline: false };
}

export async function loadCachedState(): Promise<DataState> {
  const cached = await readJson<DataState>(CACHE_FILE);
  return cached ? { ...emptyState(), ...cached } : emptyState();
}

type FetchResult<T> = { status: "ok"; body: T; etag: string | null } | { status: "not-modified" };

async function fetchJson<T>(path: string, etag: string | undefined): Promise<FetchResult<T>> {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), TIMEOUT_MS);
  try {
    const headers: Record<string, string> = { Accept: "application/json" };
    if (etag) headers["If-None-Match"] = etag;
    const res = await fetch(DATA_BASE_URL + path, { headers, signal: ctrl.signal, cache: "no-cache" });
    if (res.status === 304) return { status: "not-modified" };
    if (!res.ok) throw new Error(`HTTP ${res.status} for ${path}`);
    return { status: "ok", body: (await res.json()) as T, etag: res.headers.get("ETag") };
  } finally {
    clearTimeout(timer);
  }
}

function validIndex(x: unknown): x is IndexFile {
  const v = x as IndexFile;
  return !!v && v.schemaVersion === 1 && Array.isArray(v.regions) && v.regions.length > 0;
}

function validRegion(x: unknown, id: string): x is RegionFile {
  const v = x as RegionFile;
  return !!v && v.id === id && Array.isArray(v.days);
}

/**
 * Refreshes the index and the regions in use. Never replaces valid cached data
 * with empty or broken responses.
 */
export async function refresh(prev: DataState, regionIds: string[]): Promise<DataState> {
  const next: DataState = { ...prev, regions: { ...prev.regions }, etags: { ...prev.etags } };
  try {
    const idx = await fetchJson<IndexFile>("index.json", prev.demo ? undefined : prev.etags["index"]);
    if (idx.status === "ok") {
      if (!validIndex(idx.body)) throw new Error("invalid index");
      next.index = idx.body;
      if (idx.etag) next.etags["index"] = idx.etag;
      if (prev.demo) next.regions = {};
    }
    next.demo = false;
    for (const id of new Set(regionIds)) {
      try {
        const r = await fetchJson<RegionFile>(`regions/${id}.json`, next.regions[id] ? next.etags[id] : undefined);
        if (r.status === "ok" && validRegion(r.body, id)) {
          next.regions[id] = r.body;
          if (r.etag) next.etags[id] = r.etag;
        }
      } catch (e) {
        console.warn("region refresh failed", id, e);
      }
    }
    next.checkedAt = Date.now();
    next.offline = false;
  } catch (e) {
    console.warn("data refresh failed", e);
    next.offline = true;
    if (!next.index || next.demo) {
      next.demo = true;
      next.index = demoIndex();
      for (const id of new Set(regionIds)) next.regions[id] = demoRegion(id);
      next.checkedAt = Date.now();
    }
  }
  await writeJson(CACHE_FILE, next);
  return next;
}

/** Ensures demo regions exist for newly added places while in demo mode. */
export function ensureDemoRegions(state: DataState, regionIds: string[]): DataState {
  if (!state.demo) return state;
  const regions = { ...state.regions };
  for (const id of regionIds) regions[id] = demoRegion(id);
  return { ...state, regions };
}
