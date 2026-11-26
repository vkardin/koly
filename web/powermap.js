// Коли Є — map of where the power is on: regions when zoomed out, buildings when zoomed in.
// Shared by the website, the Android app (WebView) and the Windows widget. Drawn by MapLibre GL
// over OpenFreeMap vector tiles; the caller passes the region shapes and the published schedules.

const TEXT = {
  uk: {
    noMap: 'Карта не може відобразитися на цьому пристрої (немає WebGL).',
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
    hint: 'Наблизьте до міста, щоб побачити будинки. Натисніть на область або будинок.',
    pick: 'Оберіть область на карті, щоб побачити групи.', close: 'Закрити',
  },
  en: {
    noMap: 'The map cannot be shown on this device (no WebGL).',
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
    hint: 'Zoom in on a town to see buildings. Tap a region or a building.',
    pick: 'Pick a region on the map to see its groups.', close: 'Close',
  },
  ru: {
    noMap: 'Карта не может отобразиться на этом устройстве (нет WebGL).',
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
    hint: 'Приблизьте к городу, чтобы увидеть дома. Нажмите на область или дом.',
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

function h(tag, cls, text) {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  if (text != null) e.textContent = text;
  return e;
}

// Basemaps: OpenFreeMap (free, no key, no cookies), vector tiles drawn by MapLibre.
const STYLES = {
  dark: 'https://tiles.openfreemap.org/styles/dark',
  light: 'https://tiles.openfreemap.org/styles/positron',
};
const HOUSE_COLORS = {
  dark: { on: '#ffc24a', maybe: '#ff8a3d', off: '#7f8cff', unknown: '#8a93a8', nodata: '#323a52', line: '#0f1422', label: '#e6e9f2', halo: '#0f1422' },
  light: { on: '#f0a818', maybe: '#e0701a', off: '#3a4ac0', unknown: '#9aa3b5', nodata: '#c3c9d8', line: '#ffffff', label: '#141a2b', halo: '#ffffff' },
};
const UA_BOUNDS = [[22.0, 44.3], [40.3, 52.4]];
const HOUSES_FROM = 11.5; // zoom from which buildings are drawn
const TILE_CACHE = 80;
// Projection of the house tiles (data/houses/tiles.py).
const LON0 = 22.0, LAT0 = 52.5, K = 82.0, COS = Math.cos(48.4 * Math.PI / 180);
const toLonLat = (x, y) => [x / (COS * K) + LON0, LAT0 - y / K];
const toUnits = (lon, lat) => ({ x: (lon - LON0) * COS * K, y: (LAT0 - lat) * K });

/**
 * Creates the map. Returns { root, update(index, regions), select(id), destroy() }.
 * opts: { regions: ua-regions.json, lang, housesBase?, theme?: 'dark'|'light', maplibregl?,
 *         home?: region id, onAddPlace?(place), onSelect?(id), names? }
 */
export function createPowerMap(opts) {
  const ml = opts.maplibregl || (typeof window !== 'undefined' && window.maplibregl);
  const housesBase = opts.housesBase || null;
  const theme = opts.theme === 'light' ? 'light' : 'dark';
  const C = HOUSE_COLORS[theme];
  let at = null;
  const viewNow = () => kyivNow(at || new Date());
  const lang = TEXT[opts.lang] ? opts.lang : 'uk';
  const T = TEXT[lang];
  const names = { ...NAMES, ...(opts.names || {}) };
  const nameOf = (id) => (names[id] && names[id][lang]) || id;
  const regionsGeo = opts.regions;
  const occupied = new Set(regionsGeo.shapes.features.filter((f) => f.properties.occupied).map((f) => f.properties.id));
  for (const id of ['luhansk', 'crimea', 'sevastopol']) if (!names[id]) names[id] = { uk: id, en: id, ru: id };
  names.luhansk = names.luhansk.uk === 'luhansk' ? { uk: 'Луганська', en: 'Luhansk', ru: 'Луганская' } : names.luhansk;
  names.crimea = names.crimea.uk === 'crimea' ? { uk: 'АР Крим', en: 'Crimea', ru: 'АР Крым' } : names.crimea;

  let selected = null, house = null;
  let index = null, files = {};
  let summaries = {};

  const root = h('div', 'pm pm-' + theme);
  const stage = h('div', 'pm-stage');
  const mapEl = h('div', 'pm-map');
  const note = h('div', 'pm-note');
  note.hidden = true;
  const tip = h('div', 'pm-tip');
  tip.hidden = true;
  stage.append(mapEl, note, tip);

  const legend = h('div', 'pm-legend');
  const lgRegions = h('span', 'pm-lg-regions');
  const ramp = h('span', 'pm-ramp');
  for (const c of [...RAMP].reverse()) { const s = h('i'); s.style.background = c; ramp.append(s); }
  lgRegions.append(h('span', 'pm-lg', T.legendOn), ramp, h('span', 'pm-lg', T.legendOff), h('span', 'pm-lg pm-lg-nodata', T.legendNoData), h('span', 'pm-lg pm-lg-occ', T.legendOccupied));
  const lgHouses = h('span', 'pm-lg-houses');
  lgHouses.hidden = true;
  for (const [cls, text] of [['', T.legendHouses], ['on', T.legendOn], ['maybe', T.maybe], ['off', T.legendOff], ['unknown', T.legendUnknown]]) {
    lgHouses.append(h('span', cls ? 'pm-hl pm-hl-' + cls : 'pm-hl', text));
  }
  legend.append(lgRegions, lgHouses);
  const panel = h('div', 'pm-panel');
  panel.setAttribute('aria-live', 'polite');
  const foot = h('p', 'pm-foot');

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

  let noteTimer = 0;
  function flash(text, ms = 4000) {
    note.textContent = text;
    note.hidden = false;
    clearTimeout(noteTimer);
    noteTimer = setTimeout(() => { note.hidden = true; }, ms);
  }

  if (!ml || !(ml.supported ? ml.supported() : true)) {
    mapEl.append(h('p', 'pm-nomap', T.noMap));
  }

  // ---- the map ----
  const map = ml ? new ml.Map({
    container: mapEl,
    style: opts.style || STYLES[theme],
    bounds: UA_BOUNDS,
    fitBoundsOptions: { padding: 8 },
    minZoom: 4,
    maxZoom: 18.5,
    dragRotate: false,
    pitchWithRotate: false,
    touchPitch: false,
    attributionControl: { compact: true },
    maxBounds: [[14, 41], [48, 56]],
  }) : null;
  let ready = false, moved = false;
  if (map) {
    map.touchZoomRotate.disableRotation();
    map.addControl(new ml.NavigationControl({ showCompass: false }), 'top-right');
    if (housesBase) {
      // "Where am I": our own button, so the fallback to the home region works everywhere.
      const ctl = {
        onAdd() {
          const div = h('div', 'maplibregl-ctrl maplibregl-ctrl-group');
          const b = h('button', 'pm-locate');
          b.type = 'button'; b.title = T.locate; b.setAttribute('aria-label', T.locate);
          b.innerHTML = '<svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true"><path d="M12 3v3M12 18v3M3 12h3M18 12h3M12 16a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>';
          b.addEventListener('click', locate);
          div.append(b);
          return div;
        },
        onRemove() {},
      };
      map.addControl(ctl, 'top-right');
    }
    // Until someone moves the map, keep the whole country in view whatever size the map gets.
    map.on('movestart', (e) => { if (e.originalEvent) moved = true; });
    map.on('resize', () => { if (!moved) map.fitBounds(UA_BOUNDS, { padding: 8, duration: 0 }); });
    map.on('load', () => { setupLayers(); ready = true; paint(); refreshHouses(); });
    map.on('moveend', () => { refreshHouses(); updateLegend(); });
    map.on('zoom', updateLegend);
    map.on('click', onClick);
    map.on('mousemove', onHover);
    map.on('mouseout', () => { tip.hidden = true; });
  }

  function hatch() {
    const c = document.createElement('canvas');
    c.width = c.height = 8;
    const g = c.getContext('2d');
    g.fillStyle = theme === 'dark' ? '#262b38' : '#d5d9e3'; g.fillRect(0, 0, 8, 8);
    g.strokeStyle = theme === 'dark' ? '#3d4459' : '#b4bbcb'; g.lineWidth = 2;
    g.beginPath(); g.moveTo(0, 8); g.lineTo(8, 0); g.moveTo(-2, 2); g.lineTo(2, -2); g.moveTo(6, 10); g.lineTo(10, 6); g.stroke();
    return g.getImageData(0, 0, 8, 8);
  }

  function setupLayers() {
    // Our layers go under the basemap's labels, so street and place names stay readable.
    const firstLabel = (map.getStyle().layers || []).find((l) => l.type === 'symbol');
    const before = firstLabel ? firstLabel.id : undefined;
    map.addImage('pm-hatch', hatch(), { pixelRatio: 1 });
    map.addSource('regions', { type: 'geojson', data: regionsGeo.shapes, promoteId: 'id' });
    map.addLayer({
      id: 'regions-fill', type: 'fill', source: 'regions', filter: ['!', ['get', 'occupied']],
      paint: {
        'fill-color': ['coalesce', ['feature-state', 'color'], C.nodata],
        'fill-opacity': ['interpolate', ['linear'], ['zoom'], 5, 0.78, 8, 0.5, 10.5, 0.18, HOUSES_FROM, 0.06],
      },
    }, before);
    map.addLayer({
      id: 'regions-occ', type: 'fill', source: 'regions', filter: ['get', 'occupied'],
      paint: { 'fill-pattern': 'pm-hatch', 'fill-opacity': ['interpolate', ['linear'], ['zoom'], 5, 0.85, 10, 0.4] },
    }, before);
    map.addLayer({
      id: 'regions-line', type: 'line', source: 'regions',
      paint: {
        'line-color': ['case', ['boolean', ['feature-state', 'selected'], false], C.label, C.line],
        'line-width': ['case', ['boolean', ['feature-state', 'selected'], false], 2.5, 1],
        'line-opacity': 0.9,
      },
    }, before);
    map.addSource('houses', { type: 'geojson', data: { type: 'FeatureCollection', features: [] } });
    map.addLayer({
      id: 'houses', type: 'circle', source: 'houses', minzoom: HOUSES_FROM - 0.5,
      paint: {
        'circle-color': ['match', ['get', 's'], 'on', C.on, 'maybe', C.maybe, 'off', C.off, C.unknown],
        'circle-opacity': ['match', ['get', 's'], 'unknown', 0.45, 0.95],
        'circle-radius': ['interpolate', ['exponential', 1.6], ['zoom'], 11, 1.4, 14, 3, 16, 5.5, 18, 10],
        'circle-stroke-width': ['interpolate', ['linear'], ['zoom'], 14, 0, 16, 1],
        'circle-stroke-color': C.halo,
        'circle-stroke-opacity': 0.6,
      },
    }, before);
    map.addSource('pick', { type: 'geojson', data: { type: 'FeatureCollection', features: [] } });
    map.addLayer({
      id: 'pick', type: 'circle', source: 'pick',
      paint: {
        'circle-color': 'rgba(0,0,0,0)', 'circle-stroke-color': C.label, 'circle-stroke-width': 2.5,
        'circle-radius': ['interpolate', ['exponential', 1.6], ['zoom'], 11, 6, 16, 10, 18, 16],
      },
    });
    map.addSource('labels', { type: 'geojson', data: regionsGeo.labels });
    map.addLayer({
      id: 'region-labels', type: 'symbol', source: 'labels', maxzoom: 9,
      filter: ['!', ['get', 'small']],
      layout: {
        'text-field': ['get', 'name'], 'text-font': ['Noto Sans Bold'],
        'text-size': ['interpolate', ['linear'], ['zoom'], 4.5, 10, 7, 14],
        'text-allow-overlap': false,
      },
      paint: { 'text-color': C.label, 'text-halo-color': C.halo, 'text-halo-width': 1.6 },
    });
    setLabelNames();
  }

  function setLabelNames() {
    if (!ready && !map?.getSource('labels')) return;
    const src = map.getSource('labels');
    if (!src) return;
    src.setData({ type: 'FeatureCollection', features: regionsGeo.labels.features.map((f) => ({ ...f, properties: { ...f.properties, name: nameOf(f.properties.id) } })) });
  }

  function updateLegend() {
    const on = !!housesBase && map && map.getZoom() >= HOUSES_FROM - 0.5;
    lgHouses.hidden = !on;
    lgRegions.hidden = on;
  }

  // ---- buildings ----
  const tiles = new Map(); // key -> tile | Promise
  let hIndex = null, hIndexLoading = null;
  function loadIndex() {
    if (!hIndexLoading) {
      hIndexLoading = fetch(housesBase + 'index.json', { cache: 'no-cache' })
        .then((r) => (r.ok ? r.json() : Promise.reject(new Error('HTTP ' + r.status))))
        .then((j) => { hIndex = j; })
        .catch(() => { hIndexLoading = null; });
    }
    return hIndexLoading;
  }
  function unpack(t) {
    const size = hIndex.tileSize, q = hIndex.q;
    const ox = t.tile[0] * size, oy = t.tile[1] * size;
    const n = t.x.length;
    const xs = new Float64Array(n), ys = new Float64Array(n);
    let x = 0, y = 0;
    for (let i = 0; i < n; i++) { x += t.x[i]; y += t.y[i]; xs[i] = ox + x / q; ys[i] = oy + y / q; }
    return { ...t, xs, ys, key: t.tile[0] + '_' + t.tile[1], used: Date.now() };
  }
  async function loadTile(key) {
    let t = tiles.get(key);
    if (!t) {
      t = fetch(housesBase + key + '.json')
        .then((r) => (r.ok ? r.json() : Promise.reject(new Error('HTTP ' + r.status))))
        .then((j) => { const u = unpack(j); tiles.set(key, u); return u; })
        .catch((e) => { tiles.delete(key); throw e; });
      tiles.set(key, t);
    }
    const out = await t;
    out.used = Date.now();
    return out;
  }
  function prune() {
    const done = [...tiles.entries()].filter(([, t]) => !(t instanceof Promise));
    if (done.length <= TILE_CACHE) return;
    done.sort((a, b) => a[1].used - b[1].used);
    for (const [k] of done.slice(0, done.length - TILE_CACHE)) tiles.delete(k);
  }
  function keysInView() {
    const b = map.getBounds();
    const a = toUnits(b.getWest(), b.getNorth()), z = toUnits(b.getEast(), b.getSouth());
    const size = hIndex.tileSize, out = [];
    for (let tx = Math.floor(a.x / size); tx <= Math.floor(z.x / size); tx++) {
      for (let ty = Math.floor(a.y / size); ty <= Math.floor(z.y / size); ty++) {
        const k = tx + '_' + ty;
        if (hIndex.tiles[k]) out.push(k);
      }
    }
    return out;
  }
  function houseAt(t, i) {
    const gi = t.g[i];
    const set = gi >= 0 ? t.groups[gi] : null;
    const [lon, lat] = toLonLat(t.xs[i], t.ys[i]);
    return { x: t.xs[i], y: t.ys[i], lon, lat, city: t.cities[t.c[i]], street: t.streets[t.s[i]], number: t.n[i],
      region: set ? t.regions[set[0]] : null, groups: set ? set.slice(1) : [] };
  }
  // State of each group at the moment on the map.
  function stateOf(t, gi, now) {
    if (gi < 0) return 'unknown';
    const [ri, ...groups] = t.groups[gi];
    const f = files[t.regions[ri]];
    if (!f) return 'unknown';
    if (f.status === 'no_schedules') return 'on';
    const day = (f.days || []).find((d) => d.date === now.date);
    if (!day) return 'unknown';
    let worst = 'on';
    for (const g of groups) {
      const v = stateAt(day.groups[g], now.minute);
      if (v === 'off') return 'off';
      if (v === 'maybe') worst = 'maybe';
    }
    return worst;
  }
  let shown = [];
  function buildFeatures() {
    const now = viewNow();
    const feats = [];
    for (const t of shown) {
      const cache = new Map();
      for (let i = 0; i < t.xs.length; i++) {
        const gi = t.g[i];
        let s = cache.get(gi);
        if (s === undefined) { s = stateOf(t, gi, now); cache.set(gi, s); }
        feats.push({ type: 'Feature', geometry: { type: 'Point', coordinates: toLonLat(t.xs[i], t.ys[i]) }, properties: { k: t.key, i, s } });
      }
    }
    return { type: 'FeatureCollection', features: feats };
  }
  function recolorHouses() {
    if (ready && map.getSource('houses')) map.getSource('houses').setData(buildFeatures());
  }
  let refreshSeq = 0;
  async function refreshHouses() {
    if (!ready || !housesBase) return;
    const seq = ++refreshSeq;
    if (map.getZoom() < HOUSES_FROM - 0.5) {
      if (shown.length) { shown = []; recolorHouses(); }
      note.hidden = true;
      return;
    }
    await loadIndex();
    if (!hIndex || seq !== refreshSeq) return;
    const keys = keysInView().slice(0, 24);
    if (!keys.length) { flash(T.noHouses, 3000); shown = []; recolorHouses(); return; }
    const pending = keys.some((k) => !tiles.get(k) || tiles.get(k) instanceof Promise);
    if (pending) { note.textContent = T.loadingHouses; note.hidden = false; }
    const loaded = (await Promise.allSettled(keys.map(loadTile))).filter((r) => r.status === 'fulfilled').map((r) => r.value);
    if (seq !== refreshSeq) return;
    if (pending) note.hidden = true;
    prune();
    shown = loaded;
    recolorHouses();
  }
  function pick(hit) {
    if (!ready) return;
    map.getSource('pick').setData({ type: 'FeatureCollection', features: hit ? [{ type: 'Feature', geometry: { type: 'Point', coordinates: [hit.lon, hit.lat] }, properties: {} }] : [] });
  }
  const houses = {
    active: () => !!housesBase && !!map && map.getZoom() >= HOUSES_FROM - 0.5,
    pick,
    houseAt,
    async loadTiles(keys) {
      await loadIndex();
      if (!hIndex) return [];
      const r = await Promise.allSettled(keys.filter((k) => hIndex.tiles[k]).slice(0, 8).map(loadTile));
      return r.filter((x) => x.status === 'fulfilled').map((x) => x.value);
    },
  };

  // ---- clicks and hover ----
  function regionAt(point) {
    const f = map.queryRenderedFeatures(point, { layers: ['regions-fill', 'regions-occ'] })[0];
    return f ? f.properties.id : null;
  }
  function onClick(e) {
    results.hidden = true;
    if (houses.active()) {
      const r = 10;
      const fs = map.queryRenderedFeatures([[e.point.x - r, e.point.y - r], [e.point.x + r, e.point.y + r]], { layers: ['houses'] });
      let best = null, bestD = Infinity;
      for (const f of fs) {
        const p = map.project(f.geometry.coordinates);
        const d = (p.x - e.point.x) ** 2 + (p.y - e.point.y) ** 2;
        if (d < bestD) { bestD = d; best = f; }
      }
      if (best) {
        const t = tiles.get(best.properties.k);
        if (t && !(t instanceof Promise)) { selectHouse(houseAt(t, best.properties.i)); return; }
      }
    }
    const id = regionAt(e.point);
    if (id) select(id, !houses.active() && map.getZoom() < 7);
  }
  function onHover(e) {
    if (map.getZoom() >= 9 || (e.originalEvent && e.originalEvent.pointerType && e.originalEvent.pointerType !== 'mouse')) { tip.hidden = true; return; }
    const id = regionAt(e.point);
    if (!id) { tip.hidden = true; map.getCanvas().style.cursor = ''; return; }
    map.getCanvas().style.cursor = 'pointer';
    tip.replaceChildren(h('strong', null, nameOf(id)), h('span', null, statusLine(id)));
    tip.hidden = false;
    tip.style.left = Math.max(12, Math.min(stage.clientWidth - 12, e.point.x)) + 'px';
    tip.style.top = e.point.y + 'px';
    tip.classList.toggle('below', e.point.y < 70);
  }

  // ---- moving the map ----
  function regionBounds(id) {
    const f = regionsGeo.shapes.features.find((x) => x.properties.id === id);
    if (!f) return null;
    let w = 180, s = 90, e = -180, n = -90;
    const walk = (c) => { if (typeof c[0] === 'number') { w = Math.min(w, c[0]); e = Math.max(e, c[0]); s = Math.min(s, c[1]); n = Math.max(n, c[1]); } else c.forEach(walk); };
    walk(f.geometry.coordinates);
    return [[w, s], [e, n]];
  }
  function focusRegion(id) {
    moved = true;
    const b = regionBounds(id);
    if (b && map) map.fitBounds(b, { padding: 30, maxZoom: 9, duration: reduceMotion() ? 0 : 600 });
  }
  const reduceMotion = () => typeof window !== 'undefined' && window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  // Search works in house-tile units: center on (x, y) showing about w units across.
  function centerOn(x, y, w) {
    if (!map) return;
    moved = true;
    const [lon, lat] = toLonLat(x, y);
    const dlon = w / (COS * K) / 2, dlat = (w * 0.68) / K / 2;
    map.fitBounds([[lon - dlon, lat - dlat], [lon + dlon, lat + dlat]], { maxZoom: 17.5, duration: reduceMotion() ? 0 : 700 });
  }

  // ---- where am I ----
  let meMarker = null;
  function locate() {
    const fallback = () => { flash(T.locateFailed, 6000); if (opts.home) focusRegion(opts.home); };
    if (typeof navigator === 'undefined' || !navigator.geolocation) { fallback(); return; }
    flash(T.locating, 15000);
    navigator.geolocation.getCurrentPosition((pos) => {
      note.hidden = true;
      const ll = [pos.coords.longitude, pos.coords.latitude];
      if (!meMarker) {
        const dot = h('div', 'pm-me');
        dot.title = T.youAreHere;
        meMarker = new ml.Marker({ element: dot }).setLngLat(ll).addTo(map);
      } else meMarker.setLngLat(ll);
      moved = true;
      map.flyTo({ center: ll, zoom: 16, duration: reduceMotion() ? 0 : 900 });
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
      centerOn(hit.x, hit.y, 0.35);
      selectHouse(hit);
      return;
    }
    // The whole street: fit it in view.
    let x0 = Infinity, y0 = Infinity, x1 = -Infinity, y1 = -Infinity;
    for (const [t, i] of mine) { x0 = Math.min(x0, t.xs[i]); x1 = Math.max(x1, t.xs[i]); y0 = Math.min(y0, t.ys[i]); y1 = Math.max(y1, t.ys[i]); }
    centerOn((x0 + x1) / 2, (y0 + y1) / 2, Math.max(0.5, (x1 - x0) * 1.3, (y1 - y0) * 1.9));
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
    const now = kyivNow();
    const base = now.minute - (now.minute % 30);
    at = steps ? new Date(Date.now() + (base + steps * 30 - now.minute) * 60000) : null;
    range.value = String(steps);
    nowBtn.hidden = !steps;
    root.classList.toggle('is-future', !!steps);
    if (!steps) timeText.textContent = T.timeNow;
    else {
      const v = viewNow();
      timeText.textContent = fill(v.date === now.date ? T.timeToday : T.timeTomorrow, { t: hm(v.minute - (v.minute % 30)) });
    }
    paint();
  }
  range.addEventListener('input', () => setOffset(Number(range.value)));
  nowBtn.addEventListener('click', () => setOffset(0));

  function statusLine(id) {
    if (occupied.has(id)) return T.occupied;
    const s = summaries[id];
    if (!s || s.kind === 'nodata') return NO_SOURCE.has(id) ? T.noSource : T.noData;
    if (s.kind === 'notoday') return T.noToday;
    if (!s.off && !s.maybe) return T.noOutages;
    return fill(T.groupsOn, { on: s.on, total: s.total }) + (s.maybe ? ' · ' + fill(T.groupsMaybe, { n: s.maybe }) : '');
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
    x.addEventListener('click', () => { house = null; pick(null); renderPanel(); });
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
    if (!selected) { panel.append(h('p', 'pm-pick', housesBase ? T.pick + ' ' + T.zoomHouses : T.pick)); return; }
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

  function selectHouse(hit) {
    house = hit;
    pick(hit);
    renderPanel();
  }

  function select(id, focus) {
    house = null;
    pick(null);
    if (ready && selected) map.setFeatureState({ source: 'regions', id: selected }, { selected: false });
    selected = id;
    if (ready && id) map.setFeatureState({ source: 'regions', id }, { selected: true });
    renderPanel();
    if (id && focus) focusRegion(id);
    if (opts.onSelect) opts.onSelect(id);
  }

  function paint() {
    const now = viewNow();
    const byId = {};
    for (const e of (index && index.regions) || []) { byId[e.id] = e; if (e.name) names[e.id] = e.name; }
    summaries = {};
    for (const f of regionsGeo.shapes.features) {
      const id = f.properties.id;
      if (occupied.has(id)) continue;
      const s = regionSummary(byId[id], files[id], now);
      summaries[id] = s;
      if (ready) map.setFeatureState({ source: 'regions', id }, { color: s.kind === 'live' ? shareColor(s.share) : null });
    }
    if (ready) setLabelNames();
    foot.textContent = fill(T.asOf, { t: hm(now.minute) }) + ' · ' + T.hint;
    recolorHouses();
    renderPanel();
  }

  const timer = setInterval(paint, 60_000);
  paint();

  return {
    root,
    update(nextIndex, nextFiles) { index = nextIndex; files = nextFiles || {}; paint(); },
    select: (id) => select(id, true),
    resize() { if (map) map.resize(); },
    jumpTo(lon, lat, zoom) { if (map) { moved = true; map.jumpTo({ center: [lon, lat], zoom }); } },
    destroy() { clearInterval(timer); if (map) map.remove(); root.remove(); },
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
