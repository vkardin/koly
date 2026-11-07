"""DTEK Odesa Electric Networks (Odesa region), schedule embedded in the site page."""

from __future__ import annotations

from common.models import RegionSchedule

from . import _dtek_site


class DtekOem:
    id = "dtek-oem"
    region_ids = ["odesa"]
    source_url = "https://www.dtek-oem.com.ua/ua/shutdowns"

    def fetch(self) -> list[RegionSchedule]:
        return [self.parse(_dtek_site.fetch_page(self.source_url))]

    def parse(self, html: str) -> RegionSchedule:
        return _dtek_site.parse(html, "odesa")
