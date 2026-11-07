# Koly Ye data

Parsers that turn public outage schedules into the normalized JSON read by the apps.
Published at `https://vkardin.github.io/koly/v1/` by the `data` workflow.

```bash
pip install -r requirements.txt
python -m pytest -q
python run.py --site ../site          # fetch all providers and update ../site/v1
python run.py --site ../site --only dtek-oem
```

- Schema: `common/models.py` (source of truth for `v1/index.json` and `v1/regions/<id>.json`).
- Providers: `providers/`, one module per supplier. Sources and status: [SOURCES.md](SOURCES.md).
- Publishing rules: `common/publish.py`. A failed source never overwrites valid data; the region is marked `stale`.
