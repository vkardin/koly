"""Hand-maintained schedules for regions whose sources cannot be read automatically.

Each file `data/manual/<region_id>.json` looks like:
    {
      "sourceUrl": "https://www.dtek-krem.com.ua/ua/shutdowns",
      "updatedAt": "2026-11-07T18:00:00+02:00",
      "days": [{"date": "2026-11-08", "groups": {"1.1": [{"from": "08:00", "to": "12:00", "type": "off"}]}}]
    }
Days before today are ignored. When no day is left, the region is reported as
not published and the last published data stays (marked stale).

A region that has an automatic provider can also have a manual file. It is then
a fallback: it is used only while the automatic source has no schedule (it fails,
reports "schedules not in effect", or reports emergency outages without any
hours), and only while the file still has a day from today on. For example, YASNO
can switch the whole Dnipropetrovsk region to emergency mode while the DTEK site
still publishes a schedule for most of the region.
"""

from __future__ import annotations

import json
from pathlib import Path

from pydantic import BaseModel, ConfigDict, ValidationError

from common.http import NotPublished, ProviderError
from common.models import DaySchedule, Emergency, RegionSchedule
from common.regions import REGION_NAMES
from common.timeutil import today_kyiv

MANUAL_DIR = Path(__file__).resolve().parent.parent / "manual"


class ManualFile(BaseModel):
    model_config = ConfigDict(extra="forbid")
    sourceUrl: str
    updatedAt: str
    days: list[DaySchedule]


class ManualProvider:
    def __init__(self, path: Path):
        self.path = path
        self.region_ids = [path.stem]
        self.id = "manual"
        try:
            self.source_url = json.loads(path.read_text(encoding="utf-8")).get("sourceUrl", "")
        except ValueError:
            self.source_url = ""

    def fetch(self) -> list[RegionSchedule]:
        try:
            manual = ManualFile.model_validate_json(self.path.read_text(encoding="utf-8"))
        except (ValidationError, ValueError) as error:
            raise ProviderError(f"{self.path.name} is invalid: {error}") from error
        today = today_kyiv().isoformat()
        days = [d for d in manual.days if d.date >= today]
        if not days:
            raise NotPublished(f"{self.path.name} has no schedule for today or later")
        return [RegionSchedule(region_id=self.path.stem, updated_at=manual.updatedAt, days=days)]


class ManualFallback:
    """Wraps an automatic provider: a manual file fills in while the source has no schedule."""

    def __init__(self, provider, manual: dict[str, ManualProvider]):
        self.provider = provider
        self.manual = manual

    def __getattr__(self, name):
        return getattr(self.provider, name)

    def _from_manual(self, region_id: str, emergency: Emergency | None = None) -> RegionSchedule | None:
        try:
            [schedule] = self.manual[region_id].fetch()
        except (ProviderError, NotPublished):
            return None
        if emergency is not None:
            schedule = schedule.model_copy(update={"emergency": emergency})
        return schedule

    def fetch(self) -> list[RegionSchedule]:
        try:
            schedules = self.provider.fetch()
        except (ProviderError, NotPublished):
            fallback = [s for r in self.provider.region_ids if r in self.manual if (s := self._from_manual(r))]
            if fallback:
                return fallback
            raise
        result = []
        for schedule in schedules:
            if schedule.region_id in self.manual and not has_schedule(schedule):
                schedule = self._from_manual(schedule.region_id, schedule.emergency) or schedule
            result.append(schedule)
        return result


def has_schedule(schedule: RegionSchedule) -> bool:
    """False when the source says schedules do not apply or gives emergency mode without hours."""
    if schedule.no_schedules:
        return False
    if schedule.emergency.active:
        return any(intervals for day in schedule.days for intervals in day.groups.values())
    return True


def manual_providers(directory: Path = MANUAL_DIR) -> list[ManualProvider]:
    if not directory.exists():
        return []
    return [ManualProvider(p) for p in sorted(directory.glob("*.json")) if p.stem in REGION_NAMES]
