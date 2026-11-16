// Коли Є — interactive map of where the power is on right now, by region.
// Shared by the website (loaded as an ES module) and the desktop widget (bundled by Vite).
// No dependencies, no network access of its own: the caller passes the geometry and the
// published schedule files (/koly/v1/index.json and regions/<id>.json).

const SVG = 'http://www.w3.org/2000/svg';
const MAX_ZOOM = 7;

const TEXT = {
  uk: {
    title: 'Карта світла', zoomIn: 'Наблизити', zoomOut: 'Віддалити', reset: 'Уся країна',
    legendOn: 'світло є', legendOff: 'світла немає', legendNoData: 'немає графіка', legendOccupied: 'тимчасово окупована',
    groupsOn: 'Світло є у {on} з {total} груп', groupsMaybe: 'можливе відключення: {n}',
    noOutages: 'Відключень за графіком зараз немає', noData: 'Графік для цієї області ще не підключено',
    noToday: 'На сьогодні графік ще не опубліковано', occupied: 'Тимчасово окупована територія',
    stale: 'Дані можуть бути застарілими', emergency: 'Діють аварійні відключення',
    until: 'до {t}', on: 'є', off: 'немає', maybe: 'можливо', updated: 'Оновлено о {t}', asOf: 'Станом на {t} за Києвом',
    hint: 'Колесо миші або щипок: масштаб. Перетягуйте, щоб рухати карту. Натисніть на область.',
    pick: 'Оберіть область на карті, щоб побачити групи.', close: 'Закрити',
  },
  en: {
    title: 'Power map', zoomIn: 'Zoom in', zoomOut: 'Zoom out', reset: 'Whole country',
    legendOn: 'power on', legendOff: 'no power', legendNoData: 'no schedule', legendOccupied: 'temporarily occupied',
    groupsOn: 'Power on in {on} of {total} groups', groupsMaybe: 'possible outage: {n}',
    noOutages: 'No scheduled outages right now', noData: 'This region is not connected yet',
    noToday: "Today's schedule is not published yet", occupied: 'Temporarily occupied territory',
    stale: 'Data may be out of date', emergency: 'Emergency outages are in effect',
    until: 'until {t}', on: 'on', off: 'off', maybe: 'maybe', updated: 'Updated at {t}', asOf: 'As of {t} Kyiv time',
    hint: 'Scroll or pinch to zoom. Drag to move the map. Tap a region.',
    pick: 'Pick a region on the map to see its groups.', close: 'Close',
  },
  ru: {
    title: 'Карта света', zoomIn: 'Приблизить', zoomOut: 'Отдалить', reset: 'Вся страна',
    legendOn: 'свет есть', legendOff: 'света нет', legendNoData: 'нет графика', legendOccupied: 'временно оккупирована',
    groupsOn: 'Свет есть в {on} из {total} групп', groupsMaybe: 'возможное отключение: {n}',
    noOutages: 'Отключений по графику сейчас нет', noData: 'График для этой области ещё не подключён',
    noToday: 'На сегодня график ещё не опубликован', occupied: 'Временно оккупированная территория',
    stale: 'Данные могут быть устаревшими', emergency: 'Действуют аварийные отключения',
    until: 'до {t}', on: 'есть', off: 'нет', maybe: 'возможно', updated: 'Обновлено в {t}', asOf: 'По состоянию на {t} по Киеву',
    hint: 'Колесо мыши или щипок: масштаб. Перетаскивайте, чтобы двигать карту. Нажмите на область.',
    pick: 'Выберите область на карте, чтобы увидеть группы.', close: 'Закрыть',
  },
};

// Region names, so regions without published data are still labelled. The published index wins.
const NAMES = {
  'kyiv-city': { uk: 'м. Київ', en: 'Kyiv city', ru: 'г. Киев' },
  'vinnytsia': { uk: 'Вінницька', en: 'Vinnytsia', ru: 'Винницкая' },
  'volyn': { uk: 'Волинська', en: 'Volyn', ru: 'Волынская' },
  'dnipropetrovsk': { uk: 'Дніпропетровська', en: 'Dnipropetrovsk', ru: 'Днепропетровская' },
  'donetsk': { uk: 'Донецька', en: 'Donetsk', ru: 'Донецкая' },
  'zhytomyr': { uk: 'Житомирська', en: 'Zhytomyr', ru: 'Житомирская' },
  'zakarpattia': { uk: 'Закарпатська', en: 'Zakarpattia', ru: 'Закарпатская' },
  'zaporizhzhia': { uk: 'Запорізька', en: 'Zaporizhzhia', ru: 'Запорожская' },
  'ivano-frankivsk': { uk: 'Івано-Франківська', en: 'Ivano-Frankivsk', ru: 'Ивано-Франковская' },
  'kyiv': { uk: 'Київська', en: 'Kyiv region', ru: 'Киевская' },
  'kirovohrad': { uk: 'Кіровоградська', en: 'Kirovohrad', ru: 'Кировоградская' },
  'lviv': { uk: 'Львівська', en: 'Lviv', ru: 'Львовская' },
  'mykolaiv': { uk: 'Миколаївська', en: 'Mykolaiv', ru: 'Николаевская' },
  'odesa': { uk: 'Одеська', en: 'Odesa', ru: 'Одесская' },
  'poltava': { uk: 'Полтавська', en: 'Poltava', ru: 'Полтавская' },
  'rivne': { uk: 'Рівненська', en: 'Rivne', ru: 'Ровенская' },
  'sumy': { uk: 'Сумська', en: 'Sumy', ru: 'Сумская' },
  'ternopil': { uk: 'Тернопільська', en: 'Ternopil', ru: 'Тернопольская' },
  'kharkiv': { uk: 'Харківська', en: 'Kharkiv', ru: 'Харьковская' },
  'kherson': { uk: 'Херсонська', en: 'Kherson', ru: 'Херсонская' },
  'khmelnytskyi': { uk: 'Хмельницька', en: 'Khmelnytskyi', ru: 'Хмельницкая' },
  'cherkasy': { uk: 'Черкаська', en: 'Cherkasy', ru: 'Черкасская' },
  'chernivtsi': { uk: 'Чернівецька', en: 'Chernivtsi', ru: 'Черновицкая' },
  'chernihiv': { uk: 'Чернігівська', en: 'Chernihiv', ru: 'Черниговская' },
};

const fill = (s, v) => s.replace(/\{(\w+)\}/g, (_, k) => v[k]);
const pad = (n) => String(n).padStart(2, '0');
const hm = (m) => pad(Math.floor(m / 60) % 24) + ':' + pad(Math.floor(m % 60));
const toMin = (s) => { const [h, m] = s.split(':').map(Number); return Math.min(1440, h * 60 + (m || 0)); };

/** Current date and minute in Kyiv, whatever the viewer's time zone. */
export function kyivNow(at = new Date()) {
  const p = Object.fromEntries(new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Europe/Kyiv', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
  }).formatToParts(at).map((x) => [x.type, x.value]));
  return { date: `${p.year}-${p.month}-${p.day}`, minute: Number(p.hour) * 60 + Number(p.minute) };
}

function stateAt(intervals, minute) {
  let state = 'on';
  for (const i of intervals || []) {
    if (minute >= toMin(i.from) && minute < toMin(i.to) && (i.type === 'off' || state === 'on')) state = i.type === 'off' ? 'off' : 'maybe';
  }
  return state;
}

/** State of one group at a minute of the day, and when it next changes (minute of the day, or null). */
export function groupState(intervals, minute) {
  const state = stateAt(intervals, minute);
  const edges = [...new Set((intervals || []).flatMap((i) => [toMin(i.from), toMin(i.to)]))].filter((m) => m > minute).sort((x, y) => x - y);
  for (const e of edges) if (stateAt(intervals, e) !== state) return { state, next: e };
  return { state, next: null };
}

/**
 * Summary of one region right now.
 * kind: 'occupied' | 'nodata' | 'notoday' | 'live'
 */
export function regionSummary(entry, file, now) {
  if (!entry || !file) return { kind: 'nodata' };
  const day = (file.days || []).find((d) => d.date === now.date);
  const groups = entry.groups && entry.groups.length ? entry.groups : Object.keys(day ? day.groups : {});
  const base = { status: file.status || entry.status, emergency: !!(file.emergency && file.emergency.active), updatedAt: file.updatedAt || entry.updatedAt, sourceUrl: file.sourceUrl };
  if (file.status === 'no_schedules' || entry.status === 'no_schedules') {
    return { ...base, kind: 'live', share: 1, on: groups.length, off: 0, maybe: 0, total: groups.length, groups: groups.map((g) => ({ id: g, state: 'on', next: null })) };
  }
  if (!day) return { ...base, kind: 'notoday' };
  const list = groups.map((g) => ({ id: g, ...groupState(day.groups[g], now.minute) }));
  const on = list.filter((g) => g.state === 'on').length;
  const maybe = list.filter((g) => g.state === 'maybe').length;
  const total = list.length || 1;
  return { ...base, kind: 'live', share: (on + maybe * 0.5) / total, on, off: total - on - maybe, maybe, total: list.length, groups: list };
}

// Colour ramp from "no power" slate to "power on" amber, in five steps.
const RAMP = ['#2a3350', '#4f4a4a', '#86683c', '#c48f3a', '#ffc24a'];
function shareColor(share) { return RAMP[Math.max(0, Math.min(4, Math.round(share * 4)))]; }

function el(tag, attrs = {}, text) {
  const e = document.createElementNS(SVG, tag);
  for (const [k, v] of Object.entries(attrs)) e.setAttribute(k, v);
  if (text != null) e.textContent = text;
  return e;
}
function h(tag, cls, text) {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  if (text != null) e.textContent = text;
  return e;
}

/**
 * Creates the map. Returns { root, update(index, regions), select(id), destroy() }.
 * opts: { geo, lang, names?: {id: {uk,en,ru}}, onSelect?(id) }
 */
export function createPowerMap(opts) {
  const { geo } = opts;
  const lang = TEXT[opts.lang] ? opts.lang : 'uk';
  const T = TEXT[lang];
  const names = { ...NAMES, ...(opts.names || {}) };
  for (const r of geo.regions) if (r.name) names[r.id] = r.name;
  const nameOf = (id) => (names[id] && names[id][lang]) || id;

  const [vx, vy, vw, vh] = geo.viewBox;
  const pad0 = 8;
  const full = { x: vx - pad0, y: vy - pad0, w: vw + pad0 * 2, h: vh + pad0 * 2 };
  let view = { ...full };
  let selected = null;
  let index = null, files = {};
  let summaries = {};

  const root = h('div', 'pm');
  root.innerHTML = '';
  const stage = h('div', 'pm-stage');
  const svg = el('svg', { class: 'pm-svg', role: 'img', 'aria-label': T.title, preserveAspectRatio: 'xMidYMid meet' });
  const defs = el('defs');
  const pat = el('pattern', { id: 'pm-hatch', width: '6', height: '6', patternUnits: 'userSpaceOnUse', patternTransform: 'rotate(45)' });
  pat.append(el('rect', { width: '6', height: '6', class: 'pm-hatch-bg' }), el('line', { x1: '0', y1: '0', x2: '0', y2: '6', class: 'pm-hatch-line' }));
  defs.append(pat);
  svg.append(defs);
  const shapes = el('g', { class: 'pm-shapes' });
  const labels = el('g', { class: 'pm-labels', 'aria-hidden': 'true' });
  svg.append(shapes, labels);

  const paths = {};
  const labelEls = {};
  for (const r of geo.regions) {
    const p = el('path', { d: r.d, class: 'pm-region', 'data-id': r.id });
    if (r.occupied) {
      p.classList.add('is-occupied');
    } else {
      p.setAttribute('tabindex', '0');
      p.setAttribute('role', 'button');
    }
    shapes.append(p);
    paths[r.id] = p;
    if (r.id === 'sevastopol') continue;
    const [lx, ly] = r.id === 'kyiv' ? [r.label[0] - 26, r.label[1] + 22] : r.label;
    const t = el('text', { x: lx, y: ly, class: 'pm-label' + (r.id === 'kyiv-city' ? ' is-small' : '') });
    labels.append(t);
    labelEls[r.id] = t;
  }

  const controls = h('div', 'pm-controls');
  const btn = (label, glyph, fn) => {
    const b = h('button', 'pm-btn');
    b.type = 'button';
    b.title = label;
    b.setAttribute('aria-label', label);
    b.innerHTML = glyph;
    b.addEventListener('click', fn);
    controls.append(b);
    return b;
  };
  const icon = (d) => `<svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true"><path d="${d}" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>`;
  btn(T.zoomIn, icon('M12 5v14M5 12h14'), () => zoomBy(1.6));
  btn(T.zoomOut, icon('M5 12h14'), () => zoomBy(1 / 1.6));
  btn(T.reset, icon('M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5'), () => animateTo(full));

  const tip = h('div', 'pm-tip');
  tip.hidden = true;
  stage.append(svg, controls, tip);

  const legend = h('div', 'pm-legend');
  const ramp = h('span', 'pm-ramp');
  for (const c of [...RAMP].reverse()) { const s = h('i'); s.style.background = c; ramp.append(s); }
  const lgOn = h('span', 'pm-lg', T.legendOn), lgOff = h('span', 'pm-lg', T.legendOff);
  const lgNo = h('span', 'pm-lg pm-lg-nodata', T.legendNoData), lgOcc = h('span', 'pm-lg pm-lg-occ', T.legendOccupied);
  legend.append(lgOn, ramp, lgOff, lgNo, lgOcc);

  const panel = h('div', 'pm-panel');
  panel.setAttribute('aria-live', 'polite');
  const foot = h('p', 'pm-foot');
  root.append(stage, legend, panel, foot);

  // ---- view box, zoom and pan ----
  function applyView() {
    svg.setAttribute('viewBox', `${view.x} ${view.y} ${view.w} ${view.h}`);
    // Labels keep a readable on-screen size; on a small map they appear once zoomed in.
    const r = svg.getBoundingClientRect();
    const s = r.width ? Math.min(r.width / view.w, r.height / view.h) : 1;
    labels.setAttribute('font-size', String(12 / s));
    labels.classList.toggle('is-hidden', s < 0.55);
    labels.classList.toggle('is-zoomed', s > 1.9);
  }
  function clamp(v) {
    const w = Math.min(full.w, Math.max(full.w / MAX_ZOOM, v.w));
    const hh = w * (full.h / full.w);
    const x = Math.min(full.x + full.w - w * 0.3, Math.max(full.x - w * 0.7, v.x));
    const y = Math.min(full.y + full.h - hh * 0.3, Math.max(full.y - hh * 0.7, v.y));
    return { x, y, w, h: hh };
  }
  function svgPoint(clientX, clientY) {
    const r = svg.getBoundingClientRect();
    // preserveAspectRatio meet: work out the drawn box inside the element.
    const s = Math.min(r.width / view.w, r.height / view.h);
    const ox = r.left + (r.width - view.w * s) / 2, oy = r.top + (r.height - view.h * s) / 2;
    return { x: view.x + (clientX - ox) / s, y: view.y + (clientY - oy) / s, s };
  }
  function zoomAt(factor, cx, cy) {
    const w = Math.min(full.w, Math.max(full.w / MAX_ZOOM, view.w / factor));
    const f = w / view.w;
    view = clamp({ x: cx - (cx - view.x) * f, y: cy - (cy - view.y) * f, w, h: w * (full.h / full.w) });
    applyView();
  }
  function zoomBy(factor) {
    const target = { ...view };
    const w = Math.min(full.w, Math.max(full.w / MAX_ZOOM, view.w / factor));
    const cx = view.x + view.w / 2, cy = view.y + view.h / 2;
    target.w = w; target.h = w * (full.h / full.w); target.x = cx - w / 2; target.y = cy - target.h / 2;
    animateTo(clamp(target));
  }
  let anim = 0;
  function animateTo(target) {
    cancelAnimationFrame(anim);
    const from = { ...view }, start = performance.now();
    const reduce = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const dur = reduce ? 0 : 260;
    const step = (t) => {
      const p = dur ? Math.min(1, (t - start) / dur) : 1;
      const e = 1 - Math.pow(1 - p, 3);
      view = { x: from.x + (target.x - from.x) * e, y: from.y + (target.y - from.y) * e, w: from.w + (target.w - from.w) * e, h: from.h + (target.h - from.h) * e };
      applyView();
      if (p < 1) anim = requestAnimationFrame(step);
    };
    anim = requestAnimationFrame(step);
  }
  function focusRegion(id) {
    const p = paths[id];
    if (!p) return;
    const b = p.getBBox();
    const w = Math.max(full.w / MAX_ZOOM, Math.max(b.width, b.height * (full.w / full.h)) * 2.4);
    const hh = w * (full.h / full.w);
    animateTo(clamp({ x: b.x + b.width / 2 - w / 2, y: b.y + b.height / 2 - hh / 2, w, h: hh }));
  }

  svg.addEventListener('wheel', (e) => {
    e.preventDefault();
    const p = svgPoint(e.clientX, e.clientY);
    zoomAt(Math.exp(-e.deltaY * (e.deltaMode === 1 ? 0.05 : 0.0018)), p.x, p.y);
  }, { passive: false });

  const pointers = new Map();
  let drag = null, moved = false, pinch = null;
  svg.addEventListener('pointerdown', (e) => {
    if (e.button !== 0 && e.pointerType === 'mouse') return;
    pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });
    svg.setPointerCapture(e.pointerId);
    moved = false;
    if (pointers.size === 1) {
      drag = { x: e.clientX, y: e.clientY, view: { ...view } };
    } else if (pointers.size === 2) {
      const [a, b] = [...pointers.values()];
      const mid = svgPoint((a.x + b.x) / 2, (a.y + b.y) / 2);
      pinch = { dist: Math.hypot(a.x - b.x, a.y - b.y), view: { ...view }, mid };
      drag = null;
    }
  });
  svg.addEventListener('pointermove', (e) => {
    if (!pointers.has(e.pointerId)) { hover(e); return; }
    pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });
    if (pinch && pointers.size >= 2) {
      const [a, b] = [...pointers.values()];
      const d = Math.hypot(a.x - b.x, a.y - b.y);
      view = { ...pinch.view };
      zoomAt(d / pinch.dist, pinch.mid.x, pinch.mid.y);
      moved = true;
    } else if (drag) {
      const dx = e.clientX - drag.x, dy = e.clientY - drag.y;
      if (Math.abs(dx) + Math.abs(dy) > 4) moved = true;
      if (!moved) return;
      const r = svg.getBoundingClientRect();
      const s = Math.min(r.width / drag.view.w, r.height / drag.view.h);
      view = clamp({ ...drag.view, x: drag.view.x - dx / s, y: drag.view.y - dy / s });
      applyView();
      svg.classList.add('is-dragging');
      tip.hidden = true;
    }
  });
  const endPointer = (e) => {
    pointers.delete(e.pointerId);
    if (pointers.size < 2) pinch = null;
    if (!pointers.size) {
      svg.classList.remove('is-dragging');
      if (!moved && e.type === 'pointerup') {
        const target = document.elementFromPoint(e.clientX, e.clientY);
        const id = target && target.closest && target.closest('.pm-region:not(.is-occupied)') ? target.getAttribute('data-id') : null;
        const occ = target && target.classList && target.classList.contains('is-occupied') ? target.getAttribute('data-id') : null;
        if (id || occ) select(id || occ, true);
      }
      drag = null;
    }
  };
  svg.addEventListener('pointerup', endPointer);
  svg.addEventListener('pointercancel', endPointer);
  svg.addEventListener('pointerleave', () => { if (!pointers.size) tip.hidden = true; });
  svg.addEventListener('dblclick', (e) => { const p = svgPoint(e.clientX, e.clientY); e.preventDefault(); zoomAt(2, p.x, p.y); });
  shapes.addEventListener('keydown', (e) => {
    const id = e.target.getAttribute && e.target.getAttribute('data-id');
    if (id && (e.key === 'Enter' || e.key === ' ')) { e.preventDefault(); select(id, true); }
  });
  shapes.addEventListener('focusin', (e) => {
    const id = e.target.getAttribute && e.target.getAttribute('data-id');
    if (id) showTip(id, null);
  });
  shapes.addEventListener('focusout', () => { tip.hidden = true; });
  root.addEventListener('keydown', (e) => {
    if (e.target.closest && e.target.closest('input,select,textarea')) return;
    if (e.key === '+' || e.key === '=') { zoomBy(1.6); e.preventDefault(); }
    else if (e.key === '-' || e.key === '_') { zoomBy(1 / 1.6); e.preventDefault(); }
    else if (e.key === '0') { animateTo(full); e.preventDefault(); }
  });

  // ---- tooltip and details ----
  function statusLine(id) {
    const r = geo.regions.find((x) => x.id === id);
    if (r && r.occupied) return T.occupied;
    const s = summaries[id];
    if (!s || s.kind === 'nodata') return T.noData;
    if (s.kind === 'notoday') return T.noToday;
    if (!s.off && !s.maybe) return T.noOutages;
    return fill(T.groupsOn, { on: s.on, total: s.total }) + (s.maybe ? ' · ' + fill(T.groupsMaybe, { n: s.maybe }) : '');
  }
  function showTip(id, e) {
    tip.replaceChildren(h('strong', null, nameOf(id)), h('span', null, statusLine(id)));
    tip.hidden = false;
    const sr = stage.getBoundingClientRect();
    let x, y;
    if (e) { x = e.clientX - sr.left; y = e.clientY - sr.top; }
    else { const b = paths[id].getBoundingClientRect(); x = b.left + b.width / 2 - sr.left; y = b.top + b.height / 2 - sr.top; }
    tip.style.left = Math.min(sr.width - 12, Math.max(12, x)) + 'px';
    tip.style.top = y + 'px';
    tip.classList.toggle('below', y < 70);
  }
  function hover(e) {
    if (e.pointerType && e.pointerType !== 'mouse') return;
    const id = e.target.getAttribute && e.target.getAttribute('data-id');
    if (id) showTip(id, e); else tip.hidden = true;
  }

  function kyivClock(iso) {
    try { return new Intl.DateTimeFormat(lang === 'en' ? 'en-GB' : lang, { timeZone: 'Europe/Kyiv', hour: '2-digit', minute: '2-digit' }).format(new Date(iso)); } catch { return ''; }
  }

  function renderPanel() {
    panel.replaceChildren();
    if (!selected) { panel.append(h('p', 'pm-pick', T.pick)); return; }
    const head = h('div', 'pm-head');
    head.append(h('h3', null, nameOf(selected)));
    const x = h('button', 'pm-x');
    x.type = 'button';
    x.setAttribute('aria-label', T.close);
    x.textContent = '×';
    x.addEventListener('click', () => select(null));
    head.append(x);
    panel.append(head);
    panel.append(h('p', 'pm-sum', statusLine(selected)));
    const s = summaries[selected];
    if (!s || s.kind !== 'live') return;
    if (s.emergency) panel.append(h('p', 'pm-warn', T.emergency));
    if (s.status === 'stale') panel.append(h('p', 'pm-warn', T.stale));
    const ul = h('ul', 'pm-groups');
    for (const g of s.groups) {
      const li = h('li', 'pm-g');
      li.dataset.state = g.state;
      li.append(h('b', null, g.id), h('span', null, T[g.state] + (g.next != null ? ' · ' + fill(T.until, { t: hm(g.next) }) : '')));
      ul.append(li);
    }
    panel.append(ul);
    if (s.updatedAt) panel.append(h('p', 'pm-upd', fill(T.updated, { t: kyivClock(s.updatedAt) })));
  }

  function select(id, focus) {
    selected = id;
    for (const [rid, p] of Object.entries(paths)) p.classList.toggle('is-selected', rid === id);
    renderPanel();
    if (id && focus) focusRegion(id);
    if (opts.onSelect) opts.onSelect(id);
  }

  function paint() {
    const now = kyivNow();
    const byId = {};
    for (const e of (index && index.regions) || []) byId[e.id] = e;
    for (const e of (index && index.regions) || []) if (e.name) names[e.id] = e.name;
    summaries = {};
    for (const r of geo.regions) {
      const p = paths[r.id];
      const lab = labelEls[r.id];
      if (lab) lab.textContent = nameOf(r.id);
      if (r.occupied) { p.style.fill = ''; continue; }
      const s = regionSummary(byId[r.id], files[r.id], now);
      summaries[r.id] = s;
      p.classList.toggle('is-nodata', s.kind !== 'live');
      p.classList.toggle('is-stale', s.status === 'stale');
      p.style.fill = s.kind === 'live' ? shareColor(s.share) : '';
      if (lab) lab.classList.toggle('on-light', s.kind === 'live' && s.share >= 0.6);
      p.setAttribute('aria-label', nameOf(r.id) + '. ' + statusLine(r.id));
    }
    foot.textContent = fill(T.asOf, { t: hm(now.minute) }) + ' · ' + T.hint;
    renderPanel();
  }

  const timer = setInterval(paint, 60_000);
  const ro = typeof ResizeObserver === 'function' ? new ResizeObserver(() => applyView()) : null;
  if (ro) ro.observe(svg);
  applyView();
  paint();

  return {
    root,
    update(nextIndex, nextFiles) { index = nextIndex; files = nextFiles || {}; paint(); },
    select: (id) => select(id, true),
    destroy() { clearInterval(timer); if (ro) ro.disconnect(); cancelAnimationFrame(anim); root.remove(); },
  };
}

/** Loads the index and every region file from a base URL ending in "/". */
export async function loadAll(base, fetchImpl = fetch) {
  const get = async (p) => { const r = await fetchImpl(base + p, { cache: 'no-cache' }); if (!r.ok) throw new Error('HTTP ' + r.status); return r.json(); };
  const index = await get('index.json');
  const files = {};
  await Promise.all(index.regions.map(async (e) => { try { files[e.id] = await get(`regions/${e.id}.json`); } catch { /* region stays grey */ } }));
  return { index, files };
}
