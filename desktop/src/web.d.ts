// Types for the power map shared with the website (web/powermap.js, web/ua-regions.json).
declare module "@web/powermap.js" {
  import type { IndexFile, LocalizedName, RegionFile } from "./core/types";
  export interface MapRegions {
    shapes: { type: "FeatureCollection"; features: unknown[] };
    labels: { type: "FeatureCollection"; features: unknown[] };
  }
  export interface PowerMap {
    root: HTMLElement;
    update(index: IndexFile | null, files: Record<string, RegionFile>): void;
    select(id: string | null): void;
    resize(): void;
    destroy(): void;
  }
  export function createPowerMap(opts: {
    regions: MapRegions;
    lang: string;
    /** The MapLibre GL module; the website loads it as a global instead. */
    maplibregl?: unknown;
    theme?: "dark" | "light";
    names?: Record<string, LocalizedName>;
    onSelect?: (id: string | null) => void;
    /** Base URL of the house tiles; without it the map shows regions only. */
    housesBase?: string;
    /** Region to show when "Where am I" cannot get the location. */
    home?: string | null;
    /** Shown as a button on a building; returns false when the place could not be added. */
    onAddPlace?: (place: { regionId: string; group: string; label: string }) => boolean | Promise<boolean>;
  }): PowerMap;
  export function loadAll(base: string, fetchImpl?: typeof fetch): Promise<{ index: IndexFile; files: Record<string, RegionFile> }>;
}

declare module "@web/ua-regions.json" {
  import type { MapRegions } from "@web/powermap.js";
  const regions: MapRegions;
  export default regions;
}

declare module "@web/powermap.css";
