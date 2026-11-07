"""DTEK Dnipro Electric Networks (Dnipropetrovsk region), via the public YASNO API."""

from __future__ import annotations

from common.models import RegionSchedule

from . import _yasno


class DtekDnem:
    id = "dtek-dnem"
    region_ids = ["dnipropetrovsk"]
    source_url = "https://www.dtek-dnem.com.ua/ua/shutdowns"
    api_url = _yasno.api_url(region=3, dso=301)

    def fetch(self) -> list[RegionSchedule]:
        return [self.parse(_yasno.fetch_payload(self.api_url))]

    def parse(self, payload: dict) -> RegionSchedule:
        return _yasno.parse(payload, "dnipropetrovsk")
