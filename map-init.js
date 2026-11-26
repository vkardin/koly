// Mounts the power map on the site. Reads the same published files as the live status card.
import { createPowerMap, loadAll } from '/koly/powermap.js?v=3';

const host = document.querySelector('#power-map');
if (host) {
  const lang = (document.documentElement.lang || 'uk').slice(0, 2);
  const base = '/koly/v1/';
  (async () => {
    const geo = await (await fetch('/koly/ua-map.json?v=1')).json();
    const map = createPowerMap({
      geo, lang, housesBase: base + 'houses/',
      onSelect(id) {
        // Picking a region on the map also switches the live status card above, when it has that region.
        const sel = document.querySelector('#w-region');
        if (id && sel && [...sel.options].some((o) => o.value === id) && sel.value !== id) {
          sel.value = id;
          sel.dispatchEvent(new Event('change'));
        }
      },
    });
    host.replaceChildren(map.root);
    const refresh = async () => {
      try { const { index, files } = await loadAll(base); map.update(index, files); host.dataset.state = 'ok'; }
      catch { host.dataset.state = 'error'; }
    };
    await refresh();
    setInterval(refresh, 5 * 60_000);
  })().catch(() => { host.dataset.state = 'error'; });
}
