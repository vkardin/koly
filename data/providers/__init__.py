"""Provider registry. One module per supplier; add new providers to AUTOMATIC."""

from __future__ import annotations

from typing import Protocol

from common.models import RegionSchedule

from .cherkasyoblenergo import Cherkasyoblenergo
from .dtek_dnem import DtekDnem
from .dtek_kem import DtekKem
from .dtek_oem import DtekOem
from .khmelnytskoblenergo import Khmelnytskoblenergo
from .lvivoblenergo import Lvivoblenergo
from .manual import ManualFallback, manual_providers
from .mykolaivoblenergo import Mykolaivoblenergo
from .rivneoblenergo import Rivneoblenergo
from .zaporizhzhiaoblenergo import Zaporizhzhiaoblenergo
from .zhytomyroblenergo import Zhytomyroblenergo


class Provider(Protocol):
    id: str
    region_ids: list[str]
    source_url: str

    def fetch(self) -> list[RegionSchedule]: ...


AUTOMATIC: list[Provider] = [
    DtekKem(),
    DtekDnem(),
    DtekOem(),
    Lvivoblenergo(),
    Khmelnytskoblenergo(),
    Zaporizhzhiaoblenergo(),
    Zhytomyroblenergo(),
    Rivneoblenergo(),
    Cherkasyoblenergo(),
    Mykolaivoblenergo(),
]


def all_providers() -> list[Provider]:
    """Automatic providers plus manual files.

    A manual file for a region no automatic provider covers is a provider of its own;
    for a covered region it is a fallback for when the source has no schedule.
    """
    covered = {r for p in AUTOMATIC for r in p.region_ids}
    manual = {m.region_ids[0]: m for m in manual_providers()}
    automatic = [
        ManualFallback(p, manual) if any(r in manual for r in p.region_ids) else p for p in AUTOMATIC
    ]
    return automatic + [m for r, m in manual.items() if r not in covered]


PROVIDERS = AUTOMATIC
