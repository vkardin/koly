"""DTEK Dnipro Electric Networks (Dnipropetrovsk region), via the public YASNO API."""

from __future__ import annotations

from common.models import RegionSchedule

from . import _dtek_site, _yasno


class DtekDnem:
    id = "dtek-dnem"
    region_ids = ["dnipropetrovsk"]
    source_url = "https://www.dtek-dnem.com.ua/ua/shutdowns"
    api_url = _yasno.api_url(region=3, dso=301)

    def fetch(self) -> list[RegionSchedule]:
        schedule, gaps = _yasno.parse_with_gaps(_yasno.fetch_payload(self.api_url), "dnipropetrovsk")
        return [_yasno.fill_from_site(schedule, gaps, self.fetch_site)]

    def fetch_site(self) -> RegionSchedule:
        return _dtek_site.parse(_dtek_site.fetch_page(self.source_url), "dnipropetrovsk")

    def parse(self, payload: dict) -> RegionSchedule:
        return _yasno.parse(payload, "dnipropetrovsk")
