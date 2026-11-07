"""Hand-maintained schedules for regions whose sources cannot be read automatically.

Each file `data/manual/<region_id>.json` looks like:
    {
      "sourceUrl": "https://www.dtek-krem.com.ua/ua/shutdowns",
      "updatedAt": "2026-11-07T18:00:00+02:00",
      "days": [{"date": "2026-11-08", "groups": {"1.1": [{"from": "08:00", "to": "12:00", "type": "off"}]}}]
    }
Days before today are ignored. When no day is left, the region is reported as
not published and the last published data stays (marked stale).
"""

from __future__ import annotations

import json
from pathlib import Path

from pydantic import BaseModel, ConfigDict, ValidationError

from common.http import NotPublished, ProviderError
from common.models import DaySchedule, RegionSchedule
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


def manual_providers(directory: Path = MANUAL_DIR) -> list[ManualProvider]:
    if not directory.exists():
        return []
    return [ManualProvider(p) for p in sorted(directory.glob("*.json")) if p.stem in REGION_NAMES]
