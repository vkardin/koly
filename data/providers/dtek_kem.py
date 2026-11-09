"""DTEK Kyiv Electric Networks (Kyiv city), via the public YASNO API."""

from __future__ import annotations

from common.models import RegionSchedule

from . import _dtek_site, _yasno


class DtekKem:
    id = "dtek-kem"
    region_ids = ["kyiv-city"]
    source_url = "https://www.dtek-kem.com.ua/ua/shutdowns"
    api_url = _yasno.api_url(region=25, dso=902)

    def fetch(self) -> list[RegionSchedule]:
        schedule, gaps = _yasno.parse_with_gaps(_yasno.fetch_payload(self.api_url), "kyiv-city")
        return [_yasno.fill_from_site(schedule, gaps, self.fetch_site)]

    def fetch_site(self) -> RegionSchedule:
        return _dtek_site.parse(_dtek_site.fetch_page(self.source_url), "kyiv-city")

    def parse(self, payload: dict) -> RegionSchedule:
        return _yasno.parse(payload, "kyiv-city")
