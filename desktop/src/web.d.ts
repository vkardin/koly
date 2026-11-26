// Types for the power map shared with the website (web/powermap.js, web/ua-map.json).
declare module "@web/powermap.js" {
  import type { IndexFile, LocalizedName, RegionFile } from "./core/types";
  export interface MapGeometry {
    viewBox: [number, number, number, number];
    regions: Array<{ id: string; d: string; label: [number, number]; occupied?: boolean; name?: LocalizedName }>;
  }
  export interface PowerMap {
    root: HTMLElement;
    update(index: IndexFile | null, files: Record<string, RegionFile>): void;
    select(id: string | null): void;
    destroy(): void;
  }
  export function createPowerMap(opts: {
    geo: MapGeometry;
    lang: string;
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

declare module "@web/ua-map.json" {
  import type { MapGeometry } from "@web/powermap.js";
  const geo: MapGeometry;
  export default geo;
}

declare module "@web/powermap.css";
