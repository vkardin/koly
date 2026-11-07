export type Lang = "uk" | "en" | "ru";
export type LocalizedName = Record<Lang, string>;
export type RegionStatus = "ok" | "stale" | "error" | "no_schedules";
export type IntervalType = "off" | "maybe";

export interface RegionIndexEntry {
  id: string;
  name: LocalizedName;
  provider: string;
  groups: string[];
  updatedAt: string;
  status: RegionStatus;
}

export interface IndexFile {
  schemaVersion: number;
  generatedAt: string;
  regions: RegionIndexEntry[];
}

export interface Interval {
  from: string;
  to: string;
  type: IntervalType;
}

export interface DaySchedule {
  date: string;
  groups: Record<string, Interval[]>;
}

export interface RegionFile {
  id: string;
  provider: string;
  sourceUrl: string;
  fetchedAt: string;
  updatedAt: string;
  status: RegionStatus;
  emergency: { active: boolean; note: string | null; since: string | null };
  days: DaySchedule[];
}

export type WidgetView = "strip" | "card" | "ring";
export type WindowMode = "top" | "desktop";
export type Theme = "system" | "dark" | "light";

export interface Place {
  id: string;
  label: string;
  regionId: string;
  group: string;
}

export interface Settings {
  lang: Lang;
  view: WidgetView;
  windowMode: WindowMode;
  theme: Theme;
  opacity: number;
  scale: number;
  showTomorrow: boolean;
  lockPosition: boolean;
  places: Place[];
  mainPlaceId: string | null;
  notify: {
    beforeOff: boolean;
    beforeOn: boolean;
    scheduleChanged: boolean;
    tomorrowPublished: boolean;
    sound: boolean;
    leadMinutes: number;
    scope: "main" | "all";
    quietNight: boolean;
  };
  refreshMinutes: number;
  autostart: boolean;
  trayIcon: boolean;
  position: { x: number; y: number } | null;
}

export const MAX_PLACES = 10;

export function defaultSettings(): Settings {
  return {
    lang: "uk",
    view: "card",
    windowMode: "top",
    theme: "system",
    opacity: 92,
    scale: 100,
    showTomorrow: true,
    lockPosition: false,
    places: [],
    mainPlaceId: null,
    notify: {
      beforeOff: true,
      beforeOn: true,
      scheduleChanged: true,
      tomorrowPublished: false,
      sound: true,
      leadMinutes: 15,
      scope: "main",
      quietNight: true,
    },
    refreshMinutes: 10,
    autostart: false,
    trayIcon: true,
    position: null,
  };
}
