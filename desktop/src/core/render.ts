import { getLang, t } from "./i18n";
import { DAY_MINUTES, formatHm, type Segment, type SegmentState } from "./schedule";

export function esc(s: string): string {
  return s.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]!);
}

export function describeSegments(segments: Segment[]): string {
  return segments
    .map((s) => {
      const key = s.state === "on" ? "barOn" : s.state === "off" ? "barOff" : "barMaybe";
      return t(key, { from: formatHm(s.start), to: s.end >= DAY_MINUTES ? "24:00" : formatHm(s.end) });
    })
    .join(", ");
}

interface BarOptions {
  nowMinutes?: number | null;
  labels?: boolean;
  durations?: boolean;
  compact?: boolean;
}

/** Day strip: segments, "now" marker and labels at the real moments of change. */
export function renderBar(segments: Segment[], opts: BarOptions = {}): string {
  const pct = (m: number) => (m / DAY_MINUTES) * 100;
  const segs = segments
    .map((s) => {
      const w = pct(s.end - s.start);
      const hours = (s.end - s.start) / 60;
      const dur =
        opts.durations && s.state !== "on" && w >= 12
          ? `<span class="seg-dur">${esc(t("hours", { n: hours.toLocaleString(getLang(), { maximumFractionDigits: 1 }) }))}</span>`
          : "";
      return `<div class="seg seg-${s.state}" style="left:${pct(s.start)}%;width:${w}%">${dur}</div>`;
    })
    .join("");

  let marker = "";
  if (opts.nowMinutes != null) {
    const left = pct(opts.nowMinutes);
    const flag = opts.compact ? "" : `<span class="now-flag">${esc(t("now"))} ${formatHm(opts.nowMinutes)}</span>`;
    marker = `<div class="now-marker" style="left:${left}%">${flag}</div>`;
  }

  let labels = "";
  if (opts.labels) {
    // Merge change points that are close to each other into one label.
    const points = segments.slice(1).map((s) => s.start);
    const groups: number[][] = [];
    for (const p of points) {
      const g = groups[groups.length - 1];
      if (g && p - g[g.length - 1] < 150) g.push(p);
      else groups.push([p]);
    }
    const items = [`<span class="lbl lbl-muted" style="left:0%">00:00</span>`];
    for (const g of groups) {
      const first = g[0];
      const last = g[g.length - 1];
      const text = g.length > 1 ? `${formatHm(first)}–${formatHm(last)}` : formatHm(first);
      const center = pct((first + last) / 2);
      items.push(`<span class="lbl" style="left:${Math.min(92, Math.max(6, center))}%">${text}</span>`);
    }
    labels = `<div class="bar-labels">${items.join("")}</div>`;
  }

  const describe = describeSegments(segments);
  return `<div class="bar-wrap${opts.compact ? " compact" : ""}">
    <div class="bar" role="img" aria-label="${esc(describe)}" title="${esc(describe)}">${segs}${marker}</div>${labels}
  </div>`;
}

function arcPath(cx: number, cy: number, r: number, a0: number, a1: number): string {
  const p = (a: number) => {
    const rad = ((a - 90) * Math.PI) / 180;
    return [cx + r * Math.cos(rad), cy + r * Math.sin(rad)];
  };
  const [x0, y0] = p(a0);
  const [x1, y1] = p(a1);
  const large = a1 - a0 > 180 ? 1 : 0;
  return `M${x0.toFixed(2)} ${y0.toFixed(2)}A${r} ${r} 0 ${large} 1 ${x1.toFixed(2)} ${y1.toFixed(2)}`;
}

/** 24-hour ring with the current-time marker. Midnight is at the top. */
export function renderRing(segments: Segment[], nowMinutes: number, center: string, sub: string, state: SegmentState | "none"): string {
  const size = 200;
  const c = size / 2;
  const r = 80;
  const arcs = segments
    .map((s) => {
      const a0 = (s.start / DAY_MINUTES) * 360;
      const a1 = (s.end / DAY_MINUTES) * 360 - (s.end - s.start === DAY_MINUTES ? 0.01 : 0);
      return `<path class="ring-seg ring-${s.state}" d="${arcPath(c, c, r, a0, a1)}"/>`;
    })
    .join("");
  const na = ((nowMinutes / DAY_MINUTES) * 360 - 90) * (Math.PI / 180);
  const mx = c + r * Math.cos(na);
  const my = c + r * Math.sin(na);
  const ticks = [0, 6, 12, 18]
    .map((h) => {
      const a = ((h * 15 - 90) * Math.PI) / 180;
      const tx = c + (r - 26) * Math.cos(a);
      const ty = c + (r - 26) * Math.sin(a) + 4;
      return `<text class="ring-tick" x="${tx.toFixed(1)}" y="${ty.toFixed(1)}" text-anchor="middle">${String(h).padStart(2, "0")}</text>`;
    })
    .join("");
  return `<svg class="ring" viewBox="0 0 ${size} ${size}" role="img" aria-label="${esc(describeSegments(segments))}">
    <defs><pattern id="hatch" width="6" height="6" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
      <rect width="6" height="6" class="hatch-bg"/><rect width="3" height="6" class="hatch-fg"/></pattern></defs>
    <circle cx="${c}" cy="${c}" r="${r}" class="ring-track"/>
    ${arcs}${ticks}
    <circle cx="${mx.toFixed(2)}" cy="${my.toFixed(2)}" r="7" class="ring-now"/>
    <text x="${c}" y="${c + 4}" text-anchor="middle" class="ring-count state-${state}">${esc(center)}</text>
    <text x="${c}" y="${c + 28}" text-anchor="middle" class="ring-sub">${esc(sub)}</text>
  </svg>`;
}

/** The "Нитка" mark: Є with a glowing amber filament as its middle bar. */
export function logoMark(size = 22): string {
  return `<svg width="${size}" height="${size}" viewBox="0 0 64 64" aria-hidden="true">
    <path d="M50 14.5A24 24 0 1 0 50 49.5" fill="none" stroke="#5B6B96" stroke-width="7" stroke-linecap="round"/>
    <path d="M17 32h22" stroke="#FFC24A" stroke-width="6" stroke-linecap="round"/>
    <path d="M17 32h22" stroke="#FFE3A3" stroke-width="2" stroke-linecap="round"/>
  </svg>`;
}
