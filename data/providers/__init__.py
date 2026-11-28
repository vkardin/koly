"""Provider registry. One module per supplier; add new providers to AUTOMATIC."""

from __future__ import annotations

from typing import Protocol

from common.models import RegionSchedule

from .cherkasyoblenergo import Cherkasyoblenergo
from .chernivtsioblenergo import Chernivtsioblenergo
from .dtek_dnem import DtekDnem
from .dtek_kem import DtekKem
from .dtek_oem import DtekOem
from .khmelnytskoblenergo import Khmelnytskoblenergo
from .lvivoblenergo import Lvivoblenergo
from .manual import ManualFallback, manual_providers
from .mykolaivoblenergo import Mykolaivoblenergo
from .rivneoblenergo import Rivneoblenergo
from . import _mirror
from ._checks import Checked, library, mirror, svitlobot
from ._svitlobot import SvitlobotRegion
from ._extras import DtekExtras
from .uamonitor import REGIONS as UAMONITOR_REGIONS
from .zaporizhzhiaoblenergo import Zaporizhzhiaoblenergo
from .zhytomyroblenergo import Zhytomyroblenergo


class Provider(Protocol):
    id: str
    region_ids: list[str]
    source_url: str

    def fetch(self) -> list[RegionSchedule]: ...


def _oe(name: str) -> object:
    return mirror(_mirror.oe_outage_data(name), "oe-outage-data")


def _dtek(name: str) -> object:
    return mirror(_mirror.outage_data_ua(name), "outage-data-ua")


# Each region: its main source, then other sources it is checked against (see `_checks`).
# Library sources here are a second, independent parser of the same operator's data.
AUTOMATIC: list[Provider] = [
    Checked(DtekKem(), [_dtek("kyiv"), svitlobot()], DtekExtras("kyiv")),
    Checked(DtekDnem(), [_dtek("dnipro"), svitlobot()], DtekExtras("dnipro")),
    Checked(DtekOem(), [_dtek("odesa"), svitlobot()], DtekExtras("odesa")),
    Checked(Lvivoblenergo(), [_oe("Lvivoblenerho"), library("lviv-oe", "https://poweron.loe.lviv.ua"), svitlobot()]),
    Checked(Khmelnytskoblenergo(), [_oe("Khmelnytskoblenerho"),
                                    library("khmelnytskyi-oe", "https://hoe.com.ua/page/pogodinni-vidkljuchennja"), svitlobot()]),
    Checked(Zaporizhzhiaoblenergo(), [_oe("Zaporizhzhiaoblenergo"), library("zaporizhzhia-oe", "https://www.zoe.com.ua/outage/"), svitlobot()]),
    Checked(Zhytomyroblenergo(), [_oe("Zhytomyroblenergo"), library("zhytomyr-oe", "https://www.ztoe.com.ua/unhooking-search.php"), svitlobot()]),
    Checked(Rivneoblenergo(), [_oe("Rivneoblenergo"), library("rivne-oe", "https://www.roe.vsei.ua/disconnections"), svitlobot()]),
    Checked(Cherkasyoblenergo(), [_oe("Cherkasyoblenergo"), library("cherkasy-oe", "https://www.cherkasyoblenergo.com"), svitlobot()]),
    Checked(Mykolaivoblenergo(), [library("mykolaiv-oe", "https://off.energy.mk.ua")]),
    Checked(Chernivtsioblenergo(), [library("chernivtsi-oe", "https://oblenergo.cv.ua/shutdowns/")]),
    # No source of its own that a polite client can read (the operator's site is behind a bot challenge).
    Checked(SvitlobotRegion("svitlobot-vinnytsia", "vinnytsia"), []),
    *UAMONITOR_REGIONS,
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
