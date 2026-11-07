"""Schema v1 models. These are the source of truth for the published JSON."""

from __future__ import annotations

import re
from datetime import date as Date
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

SCHEMA_VERSION = 1

RegionStatus = Literal["ok", "stale", "error", "no_schedules"]
IntervalType = Literal["off", "maybe"]

_HM = re.compile(r"^(?:[01]\d|2[0-3]):[0-5]\d$|^24:00$")
_ISO_DATETIME = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}[+-]\d{2}:\d{2}$")


def hm_to_minutes(value: str) -> int:
    hours, minutes = value.split(":")
    return int(hours) * 60 + int(minutes)


def minutes_to_hm(value: int) -> str:
    if not 0 <= value <= 1440:
        raise ValueError(f"minutes out of range: {value}")
    return f"{value // 60:02d}:{value % 60:02d}"


class _Model(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)


class LocalizedName(_Model):
    uk: str = Field(min_length=1)
    en: str = Field(min_length=1)
    ru: str = Field(min_length=1)


class Interval(_Model):
    from_: str = Field(alias="from")
    to: str
    type: IntervalType

    @field_validator("from_", "to")
    @classmethod
    def _check_hm(cls, value: str) -> str:
        if not _HM.match(value):
            raise ValueError(f"invalid time {value!r}, expected HH:MM")
        return value

    @model_validator(mode="after")
    def _check_order(self) -> Interval:
        if self.from_ == "24:00":
            raise ValueError("interval cannot start at 24:00")
        if hm_to_minutes(self.from_) >= hm_to_minutes(self.to):
            raise ValueError(f"empty or reversed interval {self.from_}-{self.to}")
        return self


class DaySchedule(_Model):
    date: str
    groups: dict[str, list[Interval]]

    @field_validator("date")
    @classmethod
    def _check_date(cls, value: str) -> str:
        Date.fromisoformat(value)
        return value

    @field_validator("groups")
    @classmethod
    def _check_groups(cls, groups: dict[str, list[Interval]]) -> dict[str, list[Interval]]:
        if not groups:
            raise ValueError("day has no groups")
        for name, intervals in groups.items():
            if not name.strip():
                raise ValueError("empty group name")
            end = -1
            for interval in intervals:
                start = hm_to_minutes(interval.from_)
                if start < end:
                    raise ValueError(f"group {name}: intervals overlap or are unsorted")
                end = hm_to_minutes(interval.to)
        return groups


class Emergency(_Model):
    active: bool = False
    note: str | None = None
    since: str | None = None


class RegionFile(_Model):
    id: str
    provider: str
    sourceUrl: str
    fetchedAt: str
    updatedAt: str
    status: RegionStatus
    emergency: Emergency = Field(default_factory=Emergency)
    days: list[DaySchedule]

    @field_validator("fetchedAt", "updatedAt")
    @classmethod
    def _check_datetime(cls, value: str) -> str:
        if not _ISO_DATETIME.match(value):
            raise ValueError(f"invalid timestamp {value!r}")
        return value

    @model_validator(mode="after")
    def _check_days(self) -> RegionFile:
        dates = [day.date for day in self.days]
        if dates != sorted(set(dates)):
            raise ValueError("days must be unique and sorted by date")
        if self.status == "ok" and not self.days:
            raise ValueError("status ok requires at least one day")
        return self

    def groups(self) -> list[str]:
        names: set[str] = set()
        for day in self.days:
            names.update(day.groups)
        return sorted(names, key=group_sort_key)


class RegionIndexEntry(_Model):
    id: str
    name: LocalizedName
    provider: str
    groups: list[str]
    updatedAt: str
    status: RegionStatus


class IndexFile(_Model):
    schemaVersion: Literal[1] = SCHEMA_VERSION
    generatedAt: str
    regions: list[RegionIndexEntry]


def group_sort_key(name: str) -> tuple:
    """Sorts "1.1" < "1.2" < "2.1" < "10.1"; non-numeric parts sort as text."""
    parts = re.split(r"[.\-_ ]", name)
    return tuple((0, int(p), "") if p.isdigit() else (1, 0, p) for p in parts)


class RegionSchedule(_Model):
    """What a provider returns for one region on a successful fetch."""

    region_id: str
    updated_at: str
    days: list[DaySchedule]
    emergency: Emergency = Field(default_factory=Emergency)
    no_schedules: bool = False
