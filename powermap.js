// Коли Є — interactive map of where the power is on right now, by region.
// Shared by the website (loaded as an ES module) and the desktop widget (bundled by Vite).
// No dependencies, no network access of its own: the caller passes the geometry and the
// published schedule files (/koly/v1/index.json and regions/<id>.json).

const SVG = 'http://www.w3.org/2000/svg';
const REGION_ZOOM = 7; // the whole country down to a region
const HOUSE_ZOOM = 1500; // down to a street (~1 km across)
const HOUSES_BELOW = 30; // view width in map units (~40 km) under which buildings are drawn
const TILE_CACHE = 48;

const TEXT = {
  uk: {
    possible: 'За тижневим графіком можливі відключення: {t}', notice: 'Повідомлення оператора',
    tomorrowOff: 'Завтра без світла: {t}', tomorrowNone: 'Завтра відключень за графіком немає',
    locate: 'Де я', locating: 'Шукаю, де ви…', locateFailed: 'Не вдалося визначити місце. Дозвольте доступ до геолокації або знайдіть адресу пошуком.', search: 'Вулиця і номер будинку', searchNone: 'Нічого не знайдено', searchLoading: 'Завантажую вулиці…', addPlace: 'Додати до моїх місць', placeAdded: 'Додано до ваших місць', timeNow: 'Зараз', timeToday: 'Сьогодні {t}', timeTomorrow: 'Завтра {t}', timeLabel: 'Час на карті', youAreHere: 'Ви тут',
    noHouses: 'Будинки для цієї місцевості ще не підключено',
    houseTitle: 'Будинок', houseNoGroup: 'Група цього будинку невідома: оператор не дає її у відкритому довіднику або адресу не знайдено.', houseGroup: 'Група {g}', houseGroups: 'Групи {g}',
    nowOn: 'Зараз світло є', nowOff: 'Зараз світла немає', nowMaybe: 'Зараз можливе відключення', todayOff: 'Сьогодні без світла: {t}', todayNone: 'Сьогодні відключень за графіком немає',
    legendHouses: 'будинки:', legendUnknown: 'група невідома', osm: '© учасники OpenStreetMap', zoomHouses: 'Наблизьте, щоб побачити будинки.',
    agree: 'Збігається з: {s}', differs: '{s} показує інакше (оновлено {t}):', differsNoTime: '{s} показує інакше:', tomorrow: 'завтра', loadingHouses: 'Завантажую будинки…', parts: 'Частину області обслуговує {n}',
    noSource: 'Оператор не публікує графік у відкритому доступі. Дивіться сайт свого обленерго.',
    title: 'Карта світла', zoomIn: 'Наблизити', zoomOut: 'Віддалити', reset: 'Уся країна',
    legendOn: 'світло є', legendOff: 'світла немає', legendNoData: 'немає графіка', legendOccupied: 'тимчасово окупована',
    groupsOn: 'Світло є у {on} з {total} груп', groupsMaybe: 'можливе відключення: {n}',
    noOutages: 'Відключень за графіком зараз немає', noData: 'Графік для цієї області ще не підключено',
    noToday: 'На сьогодні графік ще не опубліковано', occupied: 'Тимчасово окупована територія',
    stale: 'Дані можуть бути застарілими', emergency: 'Діють аварійні відключення',
    until: 'до {t}', on: 'є', off: 'немає', maybe: 'можливо', updated: 'Оновлено о {t}', asOf: 'Станом на {t} за Києвом',
    hint: 'Колесо миші або щипок: масштаб. Наблизьте до міста, щоб побачити будинки. Натисніть на область або будинок.',
    pick: 'Оберіть область на карті, щоб побачити групи.', close: 'Закрити',
  },
  en: {
    possible: 'Possible outages by the weekly schedule: {t}', notice: 'Operator notice',
    tomorrowOff: 'Off tomorrow: {t}', tomorrowNone: 'No scheduled outages tomorrow',
    locate: 'Where am I', locating: 'Finding where you are…', locateFailed: 'Could not find your location. Allow location access or search for your address.', search: 'Street and house number', searchNone: 'Nothing found', searchLoading: 'Loading streets…', addPlace: 'Add to my places', placeAdded: 'Added to your places', timeNow: 'Now', timeToday: 'Today {t}', timeTomorrow: 'Tomorrow {t}', timeLabel: 'Time on the map', youAreHere: 'You are here',
    noHouses: 'Buildings are not available here yet',
    houseTitle: 'Building', houseNoGroup: 'This building\'s group is unknown: the operator has no open directory or the address was not found.', houseGroup: 'Group {g}', houseGroups: 'Groups {g}',
    nowOn: 'Power is on now', nowOff: 'No power now', nowMaybe: 'Possible outage now', todayOff: 'Off today: {t}', todayNone: 'No scheduled outages today',
    legendHouses: 'buildings:', legendUnknown: 'group unknown', osm: '© OpenStreetMap contributors', zoomHouses: 'Zoom in to see buildings.',
    agree: 'Matches: {s}', differs: '{s} shows something else (updated {t}):', differsNoTime: '{s} shows something else:', tomorrow: 'tomorrow', loadingHouses: 'Loading buildings…', parts: 'Part of the region is served by {n}',
    noSource: 'The operator does not publish an open schedule. Check your supplier\'s website.',
    title: 'Power map', zoomIn: 'Zoom in', zoomOut: 'Zoom out', reset: 'Whole country',
    legendOn: 'power on', legendOff: 'no power', legendNoData: 'no schedule', legendOccupied: 'temporarily occupied',
    groupsOn: 'Power on in {on} of {total} groups', groupsMaybe: 'possible outage: {n}',
    noOutages: 'No scheduled outages right now', noData: 'This region is not connected yet',
    noToday: "Today's schedule is not published yet", occupied: 'Temporarily occupied territory',
    stale: 'Data may be out of date', emergency: 'Emergency outages are in effect',
    until: 'until {t}', on: 'on', off: 'off', maybe: 'maybe', updated: 'Updated at {t}', asOf: 'As of {t} Kyiv time',
    hint: 'Scroll or pinch to zoom. Zoom in on a town to see buildings. Tap a region or a building.',
    pick: 'Pick a region on the map to see its groups.', close: 'Close',
  },
  ru: {
    possible: 'По недельному графику возможны отключения: {t}', notice: 'Сообщение оператора',
    tomorrowOff: 'Завтра без света: {t}', tomorrowNone: 'Завтра отключений по графику нет',
    locate: 'Где я', locating: 'Ищу, где вы…', locateFailed: 'Не удалось определить место. Разрешите доступ к геолокации или найдите адрес поиском.', search: 'Улица и номер дома', searchNone: 'Ничего не найдено', searchLoading: 'Загружаю улицы…', addPlace: 'Добавить в мои места', placeAdded: 'Добавлено в ваши места', timeNow: 'Сейчас', timeToday: 'Сегодня {t}', timeTomorrow: 'Завтра {t}', timeLabel: 'Время на карте', youAreHere: 'Вы здесь',
    noHouses: 'Дома для этой местности ещё не подключены',
    houseTitle: 'Дом', houseNoGroup: 'Группа этого дома неизвестна: оператор не даёт её в открытом справочнике или адрес не найден.', houseGroup: 'Группа {g}', houseGroups: 'Группы {g}',
    nowOn: 'Сейчас свет есть', nowOff: 'Сейчас света нет', nowMaybe: 'Сейчас возможно отключение', todayOff: 'Сегодня без света: {t}', todayNone: 'Сегодня отключений по графику нет',
    legendHouses: 'дома:', legendUnknown: 'группа неизвестна', osm: '© участники OpenStreetMap', zoomHouses: 'Приблизьте, чтобы увидеть дома.',
    agree: 'Совпадает с: {s}', differs: '{s} показывает иначе (обновлено {t}):', differsNoTime: '{s} показывает иначе:', tomorrow: 'завтра', loadingHouses: 'Загружаю дома…', parts: 'Часть области обслуживает {n}',
    noSource: 'Оператор не публикует график в открытом доступе. Смотрите сайт своего облэнерго.',
    title: 'Карта света', zoomIn: 'Приблизить', zoomOut: 'Отдалить', reset: 'Вся страна',
    legendOn: 'свет есть', legendOff: 'света нет', legendNoData: 'нет графика', legendOccupied: 'временно оккупирована',
    groupsOn: 'Свет есть в {on} из {total} групп', groupsMaybe: 'возможное отключение: {n}',
    noOutages: 'Отключений по графику сейчас нет', noData: 'График для этой области ещё не подключён',
    noToday: 'На сегодня график ещё не опубликован', occupied: 'Временно оккупированная территория',
    stale: 'Данные могут быть устаревшими', emergency: 'Действуют аварийные отключения',
    until: 'до {t}', on: 'есть', off: 'нет', maybe: 'возможно', updated: 'Обновлено в {t}', asOf: 'По состоянию на {t} по Киеву',
    hint: 'Колесо мыши или щипок: масштаб. Приблизьте к городу, чтобы увидеть дома. Нажмите на область или дом.',
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

// Regions whose operator publishes no schedule anyone can read (checked 2026-11-16, see data/SOURCES.md).
const NO_SOURCE = new Set(['vinnytsia', 'donetsk', 'kherson']);

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
  // Base URL of the house tiles (…/v1/houses/); without it the map shows regions only.
  const housesBase = opts.housesBase || null;
  const maxZoom = housesBase ? HOUSE_ZOOM : REGION_ZOOM;
  // The moment the map shows: now (offset 0) or up to a day and a half ahead, in minutes.
  let at = null;
  const viewNow = () => kyivNow(at || new Date());
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
  if (housesBase) btn(T.locate, icon('M12 3v3M12 18v3M3 12h3M18 12h3M12 16a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z'), () => locate());

  const tip = h('div', 'pm-tip');
  tip.hidden = true;
  const canvas = h('canvas', 'pm-canvas');
  canvas.setAttribute('aria-hidden', 'true');
  const note = h('div', 'pm-note');
  note.hidden = true;
  stage.append(svg, canvas, controls, tip, note);

  const legend = h('div', 'pm-legend');
  const ramp = h('span', 'pm-ramp');
  for (const c of [...RAMP].reverse()) { const s = h('i'); s.style.background = c; ramp.append(s); }
  const lgOn = h('span', 'pm-lg', T.legendOn), lgOff = h('span', 'pm-lg', T.legendOff);
  const lgNo = h('span', 'pm-lg pm-lg-nodata', T.legendNoData), lgOcc = h('span', 'pm-lg pm-lg-occ', T.legendOccupied);
  const lgHouses = h('span', 'pm-lg-houses');
  lgHouses.hidden = true;
  for (const [cls, text] of [['', T.legendHouses], ['on', T.legendOn], ['maybe', T.maybe], ['off', T.legendOff], ['unknown', T.legendUnknown]]) {
    const item = h('span', cls ? 'pm-hl pm-hl-' + cls : 'pm-hl', text);
    lgHouses.append(item);
  }
  legend.append(lgOn, ramp, lgOff, lgNo, lgOcc, lgHouses);

  const panel = h('div', 'pm-panel');
  panel.setAttribute('aria-live', 'polite');
  const foot = h('p', 'pm-foot');
  // Address search (needs the house layer).
  const search = h('div', 'pm-search');
  const input = h('input');
  input.type = 'search';
  input.placeholder = T.search;
  input.setAttribute('aria-label', T.search);
  input.autocomplete = 'off';
  const results = h('ul', 'pm-results');
  results.hidden = true;
  results.setAttribute('role', 'listbox');
  search.append(input, results);
  search.hidden = !housesBase;

  // Time on the map: now, or any half hour up to tomorrow evening.
  const timebar = h('div', 'pm-time');
  const range = h('input');
  range.type = 'range'; range.min = '0'; range.max = '72'; range.step = '1'; range.value = '0';
  range.setAttribute('aria-label', T.timeLabel);
  const timeText = h('span', 'pm-time-text', T.timeNow);
  const nowBtn = h('button', 'pm-now-btn', T.timeNow);
  nowBtn.type = 'button';
  nowBtn.hidden = true;
  timebar.append(range, timeText, nowBtn);

  root.append(search, stage, timebar, legend, panel, foot);

  // ---- view box, zoom and pan ----
  function applyView() {
    svg.setAttribute('viewBox', `${view.x} ${view.y} ${view.w} ${view.h}`);
    // Labels keep a readable on-screen size; on a small map they appear once zoomed in.
    const r = svg.getBoundingClientRect();
    const s = r.width ? Math.min(r.width / view.w, r.height / view.h) : 1;
    labels.setAttribute('font-size', String(12 / s));
    labels.classList.toggle('is-hidden', s < 0.55);
    labels.classList.toggle('is-zoomed', s > 1.9);
    houses.schedule();
  }
  function clamp(v) {
    const w = Math.min(full.w, Math.max(full.w / maxZoom, v.w));
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
    const w = Math.min(full.w, Math.max(full.w / maxZoom, view.w / factor));
    const f = w / view.w;
    view = clamp({ x: cx - (cx - view.x) * f, y: cy - (cy - view.y) * f, w, h: w * (full.h / full.w) });
    applyView();
  }
  function zoomBy(factor) {
    const target = { ...view };
    const w = Math.min(full.w, Math.max(full.w / maxZoom, view.w / factor));
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
    const w = Math.max(full.w / REGION_ZOOM, Math.max(b.width, b.height * (full.w / full.h)) * 2.4);
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
      if (!moved && e.type === 'pointerup' && houses.active()) {
        const hit = houses.hitTest(e.clientX, e.clientY);
        if (hit) { selectHouse(hit); drag = null; return; }
      }
      if (!moved && e.type === 'pointerup') {
        const target = document.elementFromPoint(e.clientX, e.clientY);
        const id = target && target.closest && target.closest('.pm-region:not(.is-occupied)') ? target.getAttribute('data-id') : null;
        const occ = target && target.classList && target.classList.contains('is-occupied') ? target.getAttribute('data-id') : null;
        if (id || occ) select(id || occ, !houses.active());
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

  // ---- buildings: drawn on a canvas over the regions once zoomed in to a town ----
  let me = null; // where the viewer is, in map units, once they asked
  const houses = (() => {
    const tiles = new Map(); // "tx_ty" -> tile | Promise
    let hIndex = null, indexLoading = null, frame = 0, failed = false;
    const colors = {};
    const readColors = () => {
      const cs = getComputedStyle(root);
      for (const k of ['on', 'off', 'maybe', 'unknown', 'road']) colors[k] = cs.getPropertyValue('--pm-h-' + k).trim() || '#888';
    };
    const active = () => !!housesBase && view.w < HOUSES_BELOW;

    function loadIndex() {
      if (!indexLoading) {
        indexLoading = fetch(housesBase + 'index.json', { cache: 'no-cache' })
          .then((r) => (r.ok ? r.json() : Promise.reject(new Error('HTTP ' + r.status))))
          .then((j) => { hIndex = j; schedule(); })
          .catch(() => { failed = true; indexLoading = null; });
      }
      return indexLoading;
    }

    // Unpacks a tile into absolute map units once, so drawing is a plain loop.
    function unpack(t) {
      const size = hIndex.tileSize, q = hIndex.q, qr = hIndex.qRoads;
      const ox = t.tile[0] * size, oy = t.tile[1] * size;
      const n = t.x.length;
      const xs = new Float64Array(n), ys = new Float64Array(n);
      let x = 0, y = 0;
      for (let i = 0; i < n; i++) { x += t.x[i]; y += t.y[i]; xs[i] = ox + x / q; ys[i] = oy + y / q; }
      const roads = t.roads.map((r) => {
        const pts = new Float64Array(r.length - 1);
        let px = r[1], py = r[2];
        pts[0] = ox + px / qr; pts[1] = oy + py / qr;
        for (let i = 3, j = 2; i + 1 < r.length; i += 2, j += 2) { px += r[i]; py += r[i + 1]; pts[j] = ox + px / qr; pts[j + 1] = oy + py / qr; }
        return { cls: r[0], pts };
      });
      return { ...t, xs, ys, roadsAbs: roads, used: performance.now() };
    }

    function wanted() {
      if (!hIndex) return [];
      const size = hIndex.tileSize;
      const out = [];
      for (let tx = Math.floor(view.x / size); tx <= Math.floor((view.x + view.w) / size); tx++) {
        for (let ty = Math.floor(view.y / size); ty <= Math.floor((view.y + view.h) / size); ty++) {
          const key = tx + '_' + ty;
          if (hIndex.tiles[key]) out.push(key);
        }
      }
      return out;
    }

    function ensure(keys) {
      let loading = 0;
      for (const key of keys) {
        const t = tiles.get(key);
        if (t && !(t instanceof Promise)) { t.used = performance.now(); continue; }
        if (t) { loading++; continue; }
        if (loading >= 4) break; // a few at a time; the rest on the next frame
        loading++;
        const p = fetch(housesBase + key + '.json')
          .then((r) => (r.ok ? r.json() : Promise.reject(new Error('HTTP ' + r.status))))
          .then((j) => { tiles.set(key, unpack(j)); prune(); schedule(); })
          .catch(() => { tiles.delete(key); });
        tiles.set(key, p);
      }
      return loading;
    }
    function prune() {
      const done = [...tiles.entries()].filter(([, t]) => !(t instanceof Promise));
      if (done.length <= TILE_CACHE) return;
      done.sort((a, b) => a[1].used - b[1].used);
      for (const [k] of done.slice(0, done.length - TILE_CACHE)) tiles.delete(k);
    }

    // Screen transform: map units -> CSS pixels inside the stage.
    function transform() {
      const r = svg.getBoundingClientRect();
      const s = Math.min(r.width / view.w, r.height / view.h);
      return { s, ox: (r.width - view.w * s) / 2 - view.x * s, oy: (r.height - view.h * s) / 2 - view.y * s, w: r.width, h: r.height };
    }

    // What each group is doing right now, per schedule region: {region: {group: state}}.
    let states = {};
    function computeStates() {
      const now = viewNow();
      states = {};
      for (const [rid, f] of Object.entries(files)) {
        const day = (f.days || []).find((d) => d.date === now.date);
        const noSched = f.status === 'no_schedules';
        states[rid] = { day, noSched, minute: now.minute };
      }
    }
    function stateOf(t, gi) {
      if (gi < 0) return 'unknown';
      const [ri, ...groups] = t.groups[gi];
      const st = states[t.regions[ri]];
      if (!st) return 'unknown';
      if (st.noSched) return 'on';
      if (!st.day) return 'unknown';
      let worst = 'on';
      for (const g of groups) {
        const v = stateAt(st.day.groups[g], st.minute);
        if (v === 'off') return 'off';
        if (v === 'maybe') worst = 'maybe';
      }
      return worst;
    }

    let wasOn = null;
    function draw() {
      frame = 0;
      // Zoomed in where no buildings are published: keep showing the region instead.
      const on = active() && (!hIndex || wanted().length > 0);
      if (on !== wasOn) { wasOn = on; onModeChange(); }
      root.classList.toggle('is-houses', on);
      lgHouses.hidden = !on;
      for (const x of [lgOn, ramp, lgOff, lgNo]) x.hidden = on;
      const dpr = Math.min(2, window.devicePixelRatio || 1);
      const tr = transform();
      const cw = Math.round(tr.w * dpr), ch = Math.round(tr.h * dpr);
      if (canvas.width !== cw || canvas.height !== ch) { canvas.width = cw; canvas.height = ch; }
      const ctx = canvas.getContext('2d');
      ctx.setTransform(1, 0, 0, 1, 0, 0);
      ctx.clearRect(0, 0, cw, ch);
      if (!on) {
        note.textContent = T.noHouses;
        note.hidden = !(active() && hIndex);
        drawMe(ctx, tr, dpr);
        return;
      }
      if (!hIndex) {
        if (!failed) { note.textContent = T.loadingHouses; note.hidden = false; loadIndex(); }
        return;
      }
      const keys = wanted();
      const pending = ensure(keys);
      note.textContent = T.loadingHouses;
      note.hidden = !pending;
      if (!colors.on) readColors();
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      const { s, ox, oy } = tr;
      // Streets: thin lines, wider for main roads; hidden when zoomed out far.
      ctx.lineCap = 'round'; ctx.lineJoin = 'round';
      ctx.strokeStyle = colors.road;
      for (const cls of [3, 2, 1]) {
        if (cls === 3 && s < 60) continue;
        ctx.lineWidth = Math.max(0.5, Math.min(cls === 1 ? 6 : cls === 2 ? 4 : 2.5, s * (cls === 1 ? 0.012 : cls === 2 ? 0.009 : 0.006)));
        ctx.beginPath();
        for (const key of keys) {
          const t = tiles.get(key);
          if (!t || t instanceof Promise) continue;
          for (const r of t.roadsAbs) {
            if (r.cls !== cls) continue;
            const p = r.pts;
            ctx.moveTo(p[0] * s + ox, p[1] * s + oy);
            for (let i = 2; i < p.length; i += 2) ctx.lineTo(p[i] * s + ox, p[i + 1] * s + oy);
          }
        }
        ctx.stroke();
      }
      // Buildings: one square each, batched by colour (fast enough for a whole city on a phone).
      const size = Math.max(1.6, Math.min(9, s * 0.009));
      const half = size / 2;
      const paths = { unknown: new Path2D(), on: new Path2D(), maybe: new Path2D(), off: new Path2D() };
      const W = tr.w + size, H = tr.h + size;
      for (const key of keys) {
        const t = tiles.get(key);
        if (!t || t instanceof Promise) continue;
        const stateCache = new Map();
        for (let i = 0; i < t.xs.length; i++) {
          const x = t.xs[i] * s + ox, y = t.ys[i] * s + oy;
          if (x < -size || y < -size || x > W || y > H) continue;
          const gi = t.g[i];
          let st = stateCache.get(gi);
          if (st === undefined) { st = stateOf(t, gi); stateCache.set(gi, st); }
          if (st === 'unknown') paths[st].rect(x - half * 0.7, y - half * 0.7, size * 0.7, size * 0.7);
          else paths[st].rect(x - half, y - half, size, size);
        }
      }
      for (const k of ['unknown', 'on', 'maybe', 'off']) { ctx.fillStyle = colors[k]; ctx.fill(paths[k]); }
      if (picked) {
        const x = picked.x * s + ox, y = picked.y * s + oy;
        ctx.lineWidth = 2; ctx.strokeStyle = colors.on;
        ctx.strokeRect(x - half - 3, y - half - 3, size + 6, size + 6);
      }
      drawMe(ctx, tr, dpr);
    }
    function drawMe(ctx, tr, dpr) {
      if (!me) return;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      const x = me.x * tr.s + tr.ox, y = me.y * tr.s + tr.oy;
      ctx.beginPath(); ctx.arc(x, y, 11, 0, Math.PI * 2); ctx.fillStyle = 'rgba(74,144,255,.22)'; ctx.fill();
      ctx.beginPath(); ctx.arc(x, y, 5.5, 0, Math.PI * 2); ctx.fillStyle = '#4a90ff'; ctx.fill();
      ctx.lineWidth = 2; ctx.strokeStyle = '#ffffff'; ctx.stroke();
    }
    function schedule() { if (!frame) frame = requestAnimationFrame(draw); }

    let picked = null;
    function hitTest(clientX, clientY) {
      if (!hIndex) return null;
      const r = svg.getBoundingClientRect();
      const tr = transform();
      const px = clientX - r.left, py = clientY - r.top;
      let best = null, bestD = 14 * 14;
      for (const key of wanted()) {
        const t = tiles.get(key);
        if (!t || t instanceof Promise) continue;
        for (let i = 0; i < t.xs.length; i++) {
          const dx = t.xs[i] * tr.s + tr.ox - px, dy = t.ys[i] * tr.s + tr.oy - py;
          const d = dx * dx + dy * dy;
          if (d < bestD) { bestD = d; best = { t, i }; }
        }
      }
      if (!best) return null;
      return houseAt(best.t, best.i);
    }
    function pick(hit) { picked = hit; schedule(); }

    // For the address search: load the given tiles and return them unpacked.
    async function loadTiles(keys) {
      await loadIndex();
      if (!hIndex) return [];
      const out = [];
      for (const key of keys.filter((k) => hIndex.tiles[k]).slice(0, 6)) {
        let t = tiles.get(key);
        if (!t) {
          t = fetch(housesBase + key + '.json').then((r) => (r.ok ? r.json() : Promise.reject(new Error('HTTP ' + r.status))))
            .then((j) => { const u = unpack(j); tiles.set(key, u); return u; });
          tiles.set(key, t);
        }
        try { out.push(await t); } catch { tiles.delete(key); }
      }
      prune();
      return out;
    }
    function houseAt(t, i) {
      const gi = t.g[i];
      const set = gi >= 0 ? t.groups[gi] : null;
      return { x: t.xs[i], y: t.ys[i], city: t.cities[t.c[i]], street: t.streets[t.s[i]], number: t.n[i],
        region: set ? t.regions[set[0]] : null, groups: set ? set.slice(1) : [] };
    }

    return { active, schedule, hitTest, pick, loadTiles, houseAt, refresh() { readColors(); computeStates(); schedule(); }, attribution: () => hIndex && hIndex.attribution };
  })();

  // ---- where am I ----
  const LON0 = 22.0, LAT0 = 52.5, K = 82.0, COS = Math.cos(48.4 * Math.PI / 180);
  const project = (lon, lat) => ({ x: (lon - LON0) * COS * K, y: (LAT0 - lat) * K });
  let noteTimer = 0;
  function flash(text, ms = 4000) {
    note.textContent = text;
    note.hidden = false;
    clearTimeout(noteTimer);
    noteTimer = setTimeout(() => { note.hidden = true; houses.schedule(); }, ms);
  }
  function centerOn(x, y, w) {
    const hh = w * (full.h / full.w);
    animateTo(clamp({ x: x - w / 2, y: y - hh / 2, w, h: hh }));
  }
  function locate() {
    const fallback = () => {
      flash(T.locateFailed, 6000);
      if (opts.home) focusRegion(opts.home);
    };
    if (!navigator.geolocation) { fallback(); return; }
    flash(T.locating, 15000);
    navigator.geolocation.getCurrentPosition((pos) => {
      me = project(pos.coords.longitude, pos.coords.latitude);
      note.hidden = true;
      centerOn(me.x, me.y, 3);
      houses.schedule();
    }, fallback, { enableHighAccuracy: false, timeout: 15000, maximumAge: 10 * 60000 });
  }

  // ---- address search ----
  let streetIndex = null, streetLoading = null;
  const KIND = /^(вулиця|вул|провулок|пров|проспект|просп|пр-т|бульвар|бул|площа|пл|узвіз|шосе|тупик|проїзд|набережна|улица|ул|переулок|пер|м|с|смт|місто|село)\.?$/;
  const words = (text) => text.toLowerCase().replace(/[’ʼ`]/g, "'").split(/[\s,.()"«»]+/).filter((w) => w && !KIND.test(w));
  const compactNum = (n) => n.toLowerCase().replace(/[\s\-.]/g, '');
  function loadStreets() {
    if (!streetLoading) {
      streetLoading = fetch(housesBase + 'streets.json')
        .then((r) => (r.ok ? r.json() : Promise.reject(new Error('HTTP ' + r.status))))
        .then((j) => {
          streetIndex = j.items.map(([ri, city, street, keys]) => ({
            region: j.regions[ri], city, street, keys, hay: words(street + ' ' + city).join(' '),
          }));
        })
        .catch(() => { streetLoading = null; });
    }
    return streetLoading;
  }
  function parseQuery(q) {
    const m = q.trim().match(/^(.*?)[\s,]+(\d+[\p{L}\d/\-]*)$/u);
    return m ? { name: m[1], number: m[2] } : { name: q.trim(), number: '' };
  }
  let searchSeq = 0;
  async function runSearch() {
    const seq = ++searchSeq;
    const q = parseQuery(input.value);
    const ws = words(q.name);
    if (!ws.length || ws.join('').length < 3) { results.hidden = true; return; }
    if (!streetIndex) {
      results.replaceChildren(h('li', 'pm-res-note', T.searchLoading));
      results.hidden = false;
      await loadStreets();
      if (seq !== searchSeq) return;
    }
    const found = (streetIndex || []).filter((s) => ws.every((w) => s.hay.includes(w))).slice(0, 8);
    results.replaceChildren();
    if (!found.length) results.append(h('li', 'pm-res-note', T.searchNone));
    for (const s of found) {
      const li = h('li', 'pm-res');
      li.setAttribute('role', 'option');
      li.tabIndex = 0;
      li.append(h('b', null, s.street + (q.number ? ', ' + q.number : '')), h('span', null, s.city || nameOf(s.region)));
      const go = () => openStreet(s, q.number);
      li.addEventListener('click', go);
      li.addEventListener('keydown', (e) => { if (e.key === 'Enter') go(); });
      results.append(li);
    }
    results.hidden = false;
  }
  async function openStreet(s, number) {
    results.hidden = true;
    input.blur();
    const loaded = await houses.loadTiles(s.keys);
    const mine = [];
    for (const t of loaded) {
      for (let i = 0; i < t.xs.length; i++) {
        if (t.streets[t.s[i]] === s.street && t.cities[t.c[i]] === s.city) mine.push([t, i]);
      }
    }
    if (!mine.length) { flash(T.searchNone); return; }
    const exact = number ? mine.find(([t, i]) => compactNum(t.n[i]) === compactNum(number)) : null;
    if (exact) {
      const hit = houses.houseAt(...exact);
      centerOn(hit.x, hit.y, 1.2);
      selectHouse(hit);
      return;
    }
    // The whole street: fit it in view.
    let x0 = Infinity, y0 = Infinity, x1 = -Infinity, y1 = -Infinity;
    for (const [t, i] of mine) { x0 = Math.min(x0, t.xs[i]); x1 = Math.max(x1, t.xs[i]); y0 = Math.min(y0, t.ys[i]); y1 = Math.max(y1, t.ys[i]); }
    centerOn((x0 + x1) / 2, (y0 + y1) / 2, Math.max(1.2, (x1 - x0) * 1.4, (y1 - y0) * 1.4 * (full.w / full.h)));
    if (number) flash(T.searchNone);
  }
  let searchTimer = 0;
  input.addEventListener('input', () => { clearTimeout(searchTimer); searchTimer = setTimeout(runSearch, 180); });
  input.addEventListener('focus', () => { loadStreets(); if (input.value) runSearch(); });
  input.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') { results.hidden = true; }
    else if (e.key === 'ArrowDown') { const f = results.querySelector('.pm-res'); if (f) { e.preventDefault(); f.focus(); } }
    else if (e.key === 'Enter') { const f = results.querySelector('.pm-res'); if (f) { e.preventDefault(); f.click(); } }
  });
  results.addEventListener('keydown', (e) => {
    const items = [...results.querySelectorAll('.pm-res')];
    const i = items.indexOf(document.activeElement);
    if (e.key === 'ArrowDown' && i < items.length - 1) { e.preventDefault(); items[i + 1].focus(); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); (i > 0 ? items[i - 1] : input).focus(); }
  });
  document.addEventListener('pointerdown', (e) => { if (!search.contains(e.target)) results.hidden = true; });

  // ---- time on the map ----
  function setOffset(steps) {
    // Steps of 30 minutes from the current half hour.
    const now = kyivNow();
    const base = now.minute - (now.minute % 30);
    at = steps ? new Date(Date.now() + (base + steps * 30 - now.minute) * 60000) : null;
    range.value = String(steps);
    nowBtn.hidden = !steps;
    root.classList.toggle('is-future', !!steps);
    if (!steps) timeText.textContent = T.timeNow;
    else {
      const v = viewNow();
      const t = hm(v.minute - (v.minute % 30));
      timeText.textContent = fill(v.date === now.date ? T.timeToday : T.timeTomorrow, { t });
    }
    paint();
  }
  range.addEventListener('input', () => setOffset(Number(range.value)));
  nowBtn.addEventListener('click', () => setOffset(0));

  // ---- tooltip and details ----
  function statusLine(id) {
    const r = geo.regions.find((x) => x.id === id);
    if (r && r.occupied) return T.occupied;
    const s = summaries[id];
    if (!s || s.kind === 'nodata') return NO_SOURCE.has(id) ? T.noSource : T.noData;
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

  const spans = (list) => list.map((i) => `${i.from}–${i.to}${i.type === 'maybe' ? '?' : ''}`).join(', ');

  // What other sources say about these groups today and tomorrow, when they disagree.
  function checksFor(file, groups) {
    const out = [];
    const checks = (file && file.checks) || [];
    const same = checks.filter((c) => c.result === 'same').map((c) => c.name);
    if (same.length) out.push(h('p', 'pm-src', fill(T.agree, { s: [...new Set(same)].join(', ') })));
    const now = viewNow();
    for (const c of checks) {
      if (c.result !== 'different' || !c.days || !c.days.length) continue;
      const lines = [];
      for (const d of c.days) {
        if (d.date < now.date) continue;
        for (const g of groups || Object.keys(d.groups)) {
          if (!(g in d.groups)) continue;
          const mine = ((file.days || []).find((x) => x.date === d.date) || { groups: {} }).groups[g];
          const theirs = d.groups[g];
          if (mine && spans(mine) === spans(theirs)) continue;
          lines.push(`${d.date === now.date ? '' : T.tomorrow + ' · '}${g}: ${theirs.length ? spans(theirs) : T.noOutages.toLowerCase()}`);
        }
      }
      if (!lines.length) continue;
      const box = h('div', 'pm-alt');
      const a = h('a', null, c.name);
      a.href = c.url; a.target = '_blank'; a.rel = 'noopener';
      const head = h('p');
      const label = c.updatedAt ? fill(T.differs, { s: '\u0000', t: kyivClock(c.updatedAt) }) : fill(T.differsNoTime, { s: '\u0000' });
      const [before, after] = label.split('\u0000');
      head.append(before, a, after);
      const ul = h('ul');
      for (const l of lines.slice(0, 12)) ul.append(h('li', null, l));
      box.append(head, ul);
      out.push(box);
    }
    return out;
  }

  // The operator's notice, as written, and its weekly "possible outages" for a group.
  function noticeOf(file) {
    if (!file || !file.notice) return [];
    const box = h('div', 'pm-notice');
    box.append(h('b', null, T.notice));
    for (const line of file.notice.split('\n')) box.append(h('p', null, line));
    return [box];
  }
  function possibleOf(file, group, date) {
    const day = file && (file.possible || []).find((d) => d.date === date);
    const list = day && day.groups[group];
    return list && list.length ? [h('p', 'pm-sum', fill(T.possible, { t: spans(list) }))] : [];
  }

  function renderHouse() {
    const head = h('div', 'pm-head');
    const addr = [house.street, house.number].filter(Boolean).join(', ');
    head.append(h('h3', null, addr || T.houseTitle));
    const x = h('button', 'pm-x');
    x.type = 'button';
    x.setAttribute('aria-label', T.close);
    x.textContent = '×';
    x.addEventListener('click', () => { house = null; houses.pick(null); renderPanel(); });
    head.append(x);
    panel.append(head);
    const where = [house.city, house.region && nameOf(house.region)].filter(Boolean).join(' · ');
    if (where) panel.append(h('p', 'pm-sum', where));
    if (!house.groups.length) { panel.append(h('p', 'pm-sum', T.houseNoGroup)); return; }
    const file = files[house.region];
    const g = house.groups.join(', ');
    panel.append(h('p', 'pm-hg', fill(house.groups.length > 1 ? T.houseGroups : T.houseGroup, { g })));
    const now = viewNow();
    const day = file && (file.days || []).find((d) => d.date === now.date);
    if (file && file.status === 'no_schedules') { panel.append(h('p', 'pm-now', T.noOutages)); }
    else if (!day) { panel.append(h('p', 'pm-sum', T.noToday)); }
    else {
      for (const grp of house.groups) {
        const st = groupState(day.groups[grp], now.minute);
        const line = h('p', 'pm-now');
        line.dataset.state = st.state;
        const word = at
          ? timeText.textContent + ': ' + (st.state === 'on' ? T.legendOn : st.state === 'off' ? T.legendOff : T.maybe)
          : (st.state === 'on' ? T.nowOn : st.state === 'off' ? T.nowOff : T.nowMaybe);
        line.textContent = (house.groups.length > 1 ? grp + ': ' : '') + word
          + (st.next != null ? ' · ' + fill(T.until, { t: hm(st.next) }) : '');
        panel.append(line);
        const off = day.groups[grp] || [];
        const later = now.date !== kyivNow().date;
        panel.append(h('p', 'pm-sum', off.length ? fill(later ? T.tomorrowOff : T.todayOff, { t: spans(off) }) : (later ? T.tomorrowNone : T.todayNone)));
        panel.append(...possibleOf(file, grp, now.date));
      }
    }
    if (file && file.status === 'stale') panel.append(h('p', 'pm-warn', T.stale));
    if (file && file.emergency && file.emergency.active) panel.append(h('p', 'pm-warn', T.emergency));
    panel.append(...noticeOf(file));
    panel.append(...checksFor(file, house.groups));
    if (file && file.updatedAt) panel.append(h('p', 'pm-upd', fill(T.updated, { t: kyivClock(file.updatedAt) })));
    if (opts.onAddPlace && house.groups.length) {
      const add = h('button', 'pm-add', T.addPlace);
      add.type = 'button';
      add.addEventListener('click', () => {
        const label = [house.street, house.number].filter(Boolean).join(', ');
        Promise.resolve(opts.onAddPlace({ regionId: house.region, group: house.groups[0], label })).then((ok) => {
          if (ok !== false) { add.disabled = true; add.textContent = T.placeAdded; }
        });
      });
      panel.append(add);
    }
  }

  function renderPanel() {
    panel.replaceChildren();
    if (house) { renderHouse(); return; }
    if (!selected) { panel.append(h('p', 'pm-pick', housesBase && !houses.active() ? T.pick + ' ' + T.zoomHouses : T.pick)); return; }
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
    panel.append(...noticeOf(files[selected]));
    const ul = h('ul', 'pm-groups');
    for (const g of s.groups) {
      const li = h('li', 'pm-g');
      li.dataset.state = g.state;
      li.append(h('b', null, g.id), h('span', null, T[g.state] + (g.next != null ? ' · ' + fill(T.until, { t: hm(g.next) }) : '')));
      ul.append(li);
    }
    panel.append(ul);
    panel.append(...checksFor(files[selected], null));
    if (s.updatedAt) panel.append(h('p', 'pm-upd', fill(T.updated, { t: kyivClock(s.updatedAt) })));
    // Parts of the region served by another operator (CEK in Dnipropetrovsk), with their own groups.
    for (const part of ((index && index.regions) || []).filter((e) => e.area === selected)) {
      const ps = regionSummary(part, files[part.id], viewNow());
      panel.append(h('h4', 'pm-part', nameOf(part.id)));
      if (ps.kind !== 'live') { panel.append(h('p', 'pm-sum', ps.kind === 'notoday' ? T.noToday : T.noData)); continue; }
      const pl = h('ul', 'pm-groups');
      for (const g of ps.groups) {
        const li = h('li', 'pm-g');
        li.dataset.state = g.state;
        li.append(h('b', null, g.id), h('span', null, T[g.state] + (g.next != null ? ' · ' + fill(T.until, { t: hm(g.next) }) : '')));
        pl.append(li);
      }
      panel.append(pl, ...checksFor(files[part.id], null));
    }
  }

  let house = null;
  function selectHouse(hit) {
    house = hit;
    houses.pick(hit);
    renderPanel();
  }

  function select(id, focus) {
    house = null;
    houses.pick(null);
    selected = id;
    for (const [rid, p] of Object.entries(paths)) p.classList.toggle('is-selected', rid === id);
    renderPanel();
    if (id && focus) focusRegion(id);
    if (opts.onSelect) opts.onSelect(id);
  }

  function setFoot(now = viewNow()) {
    const attribution = housesBase && houses.active() ? ' · ' + T.osm : '';
    foot.textContent = fill(T.asOf, { t: hm(now.minute) }) + ' · ' + T.hint + attribution;
  }
  function onModeChange() {
    setFoot();
    if (!selected && !house) renderPanel();
  }

  function paint() {
    const now = viewNow();
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
    setFoot(now);
    houses.refresh();
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
