// Live status from the published schedules (/koly/v1/). Same files the widget reads; nothing else is requested.
(() => {
  const root = document.querySelector('#now');
  const textNode = document.querySelector('#koly-text');
  if (!root || !textNode) return;
  const T = JSON.parse(textNode.textContent);
  const lang = (document.documentElement.lang || 'uk').slice(0, 2);
  const base = '/koly/v1/';
  const $ = s => root.querySelector(s);
  const regionSelect = $('#w-region'), groupSelect = $('#w-group');
  const state = $('.state'), word = $('.w-word'), next = $('.w-next');
  const daysBox = $('.days'), notes = $('.w-notes'), foot = $('.w-foot');
  const key = 'koly-web-place';
  let index = null, region = null, saved = {};
  try { saved = JSON.parse(localStorage.getItem(key) || '{}') || {}; } catch {}

  const fill = (s, v) => s.replace(/\{(\w+)\}/g, (_, k) => v[k]);
  const pad = n => String(n).padStart(2, '0');
  const hm = m => pad(Math.floor(m / 60) % 24) + ':' + pad(m % 60);
  const toMin = s => { const [h, m] = s.split(':').map(Number); return h * 60 + m; };

  // Current date and minute in Kyiv, whatever the visitor's time zone.
  function kyivNow() {
    const parts = Object.fromEntries(new Intl.DateTimeFormat('en-CA', {timeZone: 'Europe/Kyiv', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23'})
      .formatToParts(new Date()).map(p => [p.type, p.value]));
    return {date: `${parts.year}-${parts.month}-${parts.day}`, minute: Number(parts.hour) * 60 + Number(parts.minute)};
  }
  function addDay(date) {
    const d = new Date(date + 'T12:00:00Z'); d.setUTCDate(d.getUTCDate() + 1);
    return d.toISOString().slice(0, 10);
  }
  function duration(min) {
    const h = Math.floor(min / 60), m = min % 60;
    return (h ? h + ' ' + T.h + (m ? ' ' : '') : '') + (m || !h ? m + ' ' + T.m : '');
  }
  function kyivTime(iso) {
    try { return new Intl.DateTimeFormat(lang === 'en' ? 'en-GB' : lang, {timeZone: 'Europe/Kyiv', hour: '2-digit', minute: '2-digit'}).format(new Date(iso)); } catch { return ''; }
  }

  // The "Detailed" view of the card: each outage with its length, the day's totals, where the data comes from.
  const viewKey = 'koly-web-view';
  let mode = 'short';
  try { mode = localStorage.getItem(viewKey) === 'full' ? 'full' : 'short'; } catch {}

  // Pure: the rows and totals of one day's intervals ({from, to, type} in minutes); `now` is null for a day not begun.
  function dayModel(list, now) {
    const rows = [];
    let marked = false;
    for (const i of list) {
      let when = 'later';
      if (now != null) {
        if (i.to <= now) when = 'past';
        else if (i.from <= now) when = 'now';
        else if (!marked) { when = 'next'; marked = true; }
      }
      rows.push({from: i.from, to: i.to, type: i.type, when});
    }
    let off = 0, longest = null, edge = 0;
    for (const i of list) {
      off += i.to - i.from;
      if (i.from - edge > 0 && (!longest || i.from - edge > longest.to - longest.from)) longest = {from: edge, to: i.from};
      edge = Math.max(edge, i.to);
    }
    if (list.length && 1440 - edge > 0 && (!longest || 1440 - edge > longest.to - longest.from)) longest = {from: edge, to: 1440};
    return {rows, off, on: 1440 - off, longest: list.length ? longest : null};
  }

  function el(tag, cls, text) { const e = document.createElement(tag); if (cls) e.className = cls; if (text != null) e.textContent = text; return e; }

  function setState(kind, title, line) {
    state.dataset.state = kind; word.textContent = title; next.textContent = line || '';
  }

  function intervals(day, group) {
    if (!day) return null;
    return (day.groups[group] || []).map(i => ({from: toMin(i.from), to: toMin(i.to), type: i.type === 'maybe' ? 'maybe' : 'off'}));
  }

  function render() {
    if (!region) return;
    const group = groupSelect.value, now = kyivNow(), tomorrowDate = addDay(now.date);
    const today = region.days.find(d => d.date === now.date), tomorrow = region.days.find(d => d.date === tomorrowDate);
    const a = intervals(today, group), b = intervals(tomorrow, group);
    // One timeline over two days, with touching intervals of the same kind merged.
    const line = [...(a || []), ...((b || []).map(i => ({from: i.from + 1440, to: i.to + 1440, type: i.type})))]
      .sort((x, y) => x.from - y.from)
      .reduce((out, i) => { const last = out[out.length - 1]; if (last && last.type === i.type && last.to >= i.from) last.to = Math.max(last.to, i.to); else out.push({...i}); return out; }, []);
    if (!a) setState('unknown', T.w_day_none, '');
    else {
      const current = line.find(i => i.from <= now.minute && now.minute < i.to);
      const upcoming = line.find(i => i.from > now.minute);
      if (current && current.type === 'off') setState('off', T.w_off, fill(T.w_until_on, {t: duration(current.to - now.minute)}));
      else if (current) setState('maybe', T.w_maybe, fill(T.w_maybe_end, {at: hm(current.to)}));
      else setState('on', T.w_on, upcoming ? fill(upcoming.type === 'off' ? T.w_until_off : T.w_until_maybe, {t: duration(upcoming.from - now.minute)}) : T.w_none);
    }
    daysBox.replaceChildren();
    [[T.w_today, a, true], [T.w_tomorrow, b, false]].forEach(([label, list, isToday]) => {
      const row = el('div', 'day');
      row.append(el('div', 'day-label', label));
      const bar = el('div', 'bar'); bar.setAttribute('aria-hidden', 'true');
      if (!list) bar.classList.add('empty');
      (list || []).forEach(i => { const s = el('span', 'seg ' + i.type); s.style.left = (i.from / 14.4) + '%'; s.style.width = ((i.to - i.from) / 14.4) + '%'; bar.append(s); });
      if (isToday && list) { const n = el('span', 'nowmark'); n.style.left = (now.minute / 14.4) + '%'; bar.append(n); }
      row.append(bar);
      const desc = el('p', 'day-text');
      if (!list) desc.textContent = T.w_day_none;
      else {
        const off = list.filter(i => i.type === 'off').map(i => hm(i.from) + '–' + (i.to === 1440 ? '24:00' : hm(i.to)));
        const maybe = list.filter(i => i.type === 'maybe').map(i => hm(i.from) + '–' + (i.to === 1440 ? '24:00' : hm(i.to)));
        const parts = [];
        if (off.length) parts.push(fill(T.w_off_list, {list: off.join(', ')}));
        if (maybe.length) parts.push(fill(T.w_maybe_list, {list: maybe.join(', ')}));
        desc.textContent = parts.join('. ') || T.w_day_clear;
      }
      row.append(desc);
      daysBox.append(row);
    });
    const ticks = el('div', 'ticks'); ticks.setAttribute('aria-hidden', 'true');
    ['00', '06', '12', '18', '24'].forEach(t => ticks.append(el('span', '', t)));
    daysBox.append(ticks);
    renderDetails(region, a, b, now);
    notes.replaceChildren();
    if (region.notice) notes.append(el('p', 'note-info', region.notice));
    if (region.emergency?.active) notes.append(el('p', 'note-warn', T.w_emergency));
    if (region.status === 'stale') notes.append(el('p', 'note-warn', T.w_stale));
    if (region.status === 'no_schedules') notes.append(el('p', 'note-warn', T.w_noschedules));
    foot.replaceChildren();
    if (region.updatedAt) foot.append(fill(T.w_updated, {at: kyivTime(region.updatedAt)}) + ' ');
    if (region.sourceUrl && /^https:\/\//.test(region.sourceUrl)) { const s = el('a', '', T.w_source); s.href = region.sourceUrl; s.rel = 'noopener'; foot.append(s); }
  }

  const tabs = el('div', 'view-tabs'); tabs.setAttribute('role', 'group');
  const det = el('div', 'details');
  const tabButtons = [['short', T.w_view_short], ['full', T.w_view_full]].map(([m, label]) => {
    const b = el('button', '', label); b.type = 'button'; b.dataset.mode = m;
    b.addEventListener('click', () => { mode = m; try { localStorage.setItem(viewKey, m); } catch {} render(); });
    tabs.append(b); return b;
  });
  daysBox.before(tabs); daysBox.after(det);

  function renderDetails(region, a, b, now) {
    tabButtons.forEach(x => x.setAttribute('aria-pressed', String(x.dataset.mode === mode)));
    const full = mode === 'full';
    det.hidden = !full; daysBox.hidden = full;
    if (!full) return;
    det.replaceChildren();
    const when = {now: T.d_now, next: T.d_next, past: T.d_past, later: ''};
    const span = (i) => hm(i.from) + '–' + (i.to === 1440 ? '24:00' : hm(i.to));
    [[T.w_today, a, now.minute], [T.w_tomorrow, b, null]].forEach(([label, list, at]) => {
      const sec = el('section', 'det-day');
      sec.append(el('h3', '', label));
      if (!list) { sec.append(el('p', 'det-none', T.w_day_none)); det.append(sec); return; }
      const m = dayModel(list, at);
      const parts = [fill(T.d_summary, {off: duration(m.off) || '0 ' + T.m, on: duration(m.on) || '0 ' + T.m})];
      if (m.longest && m.rows.length) parts.push(fill(T.d_longest, {len: duration(m.longest.to - m.longest.from)}));
      sec.append(el('p', 'det-sum', parts.join(' · ')));
      if (!m.rows.length) sec.append(el('p', 'det-none', T.d_none));
      else {
        const ul = el('ul', 'det-rows');
        m.rows.forEach(r => {
          const li = el('li', 'det-row ' + r.type + ' ' + r.when);
          li.append(el('span', 'det-dot'), el('span', 'det-time', span(r)), el('span', 'det-len', duration(r.to - r.from)),
            el('span', 'det-tag', r.when === 'now' ? T.d_now : r.type === 'maybe' && r.when === 'later' ? T.d_maybe : when[r.when]));
          ul.append(li);
        });
        sec.append(ul);
      }
      det.append(sec);
    });
    const src = el('section', 'det-day');
    src.append(el('h3', '', T.d_sources));
    let host = '';
    try { host = new URL(region.sourceUrl).hostname.replace(/^www\./, ''); } catch {}
    src.append(el('p', 'det-sum', fill(T.d_main, {host: host || '—', at: region.updatedAt ? kyivTime(region.updatedAt) : '—'})));
    const checks = region.checks || [];
    src.append(el('p', 'det-sum', checks.length ? fill(T.d_checks, {n: checks.filter(c => c.result === 'same').length, total: checks.length}) : T.d_nochecks));
    if (checks.length) {
      const ul = el('ul', 'det-chips');
      const names = {same: T.d_same, different: T.d_different, older: T.d_older, failed: T.d_failed};
      checks.forEach(c => { const li = el('li', 'chip ' + c.result); li.append(el('b', '', c.name), ' ' + (names[c.result] || c.result)); ul.append(li); });
      src.append(ul);
    }
    det.append(src);
  }

  async function loadRegion(id) {
    const info = index.regions.find(r => r.id === id) || index.regions[0];
    regionSelect.value = info.id;
    const response = await fetch(base + 'regions/' + info.id + '.json', {cache: 'no-cache'});
    if (!response.ok) throw Error(response.status);
    region = await response.json();
    groupSelect.replaceChildren(...info.groups.map(g => { const o = el('option', '', g); o.value = g; return o; }));
    groupSelect.value = info.id === saved.region && info.groups.includes(saved.group) ? saved.group : info.groups[0];
    groupSelect.disabled = false;
    render();
  }

  function remember() { try { localStorage.setItem(key, JSON.stringify({region: regionSelect.value, group: groupSelect.value})); } catch {} }

  function regionStatuses() {
    document.querySelectorAll('#regions [data-region]').forEach(li => {
      const r = index.regions.find(x => x.id === li.dataset.region); if (!r) return;
      const s = li.querySelector('.rstatus'); s.dataset.status = r.status; s.textContent = T.st[r.status] || r.status;
    });
  }

  function fail() {
    setState('error', T.w_error, '');
    daysBox.replaceChildren(); notes.replaceChildren(); foot.replaceChildren();
    const retry = el('button', 'retry', T.w_retry); retry.type = 'button';
    retry.addEventListener('click', start); foot.append(retry);
  }

  async function start() {
    setState('loading', T.w_loading, '');
    try {
      const response = await fetch(base + 'index.json', {cache: 'no-cache'});
      if (!response.ok) throw Error(response.status);
      index = await response.json();
      regionSelect.replaceChildren(...index.regions.map(r => { const o = el('option', '', r.name[lang] || r.name.uk); o.value = r.id; return o; }));
      regionSelect.disabled = false;
      regionStatuses();
      const first = index.regions.find(r => r.status === 'ok') || index.regions[0];
      await loadRegion(index.regions.some(r => r.id === saved.region) ? saved.region : first.id);
    } catch { fail(); }
  }

  regionSelect.addEventListener('change', () => { saved = {}; loadRegion(regionSelect.value).then(remember).catch(fail); });
  groupSelect.addEventListener('change', () => { remember(); render(); });
  setInterval(render, 60000);
  setInterval(() => { if (index && regionSelect.value) loadRegion(regionSelect.value).catch(() => {}); }, 600000);
  start();
})();
