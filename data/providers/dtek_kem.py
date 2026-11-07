"""DTEK Kyiv Electric Networks (Kyiv city), via the public YASNO API."""

from __future__ import annotations

from common.models import RegionSchedule

from . import _yasno


class DtekKem:
    id = "dtek-kem"
    region_ids = ["kyiv-city"]
    source_url = "https://www.dtek-kem.com.ua/ua/shutdowns"
    api_url = _yasno.api_url(region=25, dso=902)

    def fetch(self) -> list[RegionSchedule]:
        return [self.parse(_yasno.fetch_payload(self.api_url))]

    def parse(self, payload: dict) -> RegionSchedule:
        return _yasno.parse(payload, "kyiv-city")
