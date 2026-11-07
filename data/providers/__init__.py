"""Provider registry. One module per supplier; add new providers to PROVIDERS."""

from __future__ import annotations

from typing import Protocol

from common.models import RegionSchedule

from .dtek_dnem import DtekDnem
from .dtek_kem import DtekKem
from .dtek_oem import DtekOem


class Provider(Protocol):
    id: str
    region_ids: list[str]
    source_url: str

    def fetch(self) -> list[RegionSchedule]: ...


PROVIDERS: list[Provider] = [DtekKem(), DtekDnem(), DtekOem()]
