"""Time helpers. All published times are local Europe/Kyiv."""

from __future__ import annotations

from datetime import date, datetime, timezone
from zoneinfo import ZoneInfo

KYIV = ZoneInfo("Europe/Kyiv")


def now_kyiv() -> datetime:
    return datetime.now(timezone.utc).astimezone(KYIV)


def iso_kyiv(moment: datetime) -> str:
    """Formats an aware datetime as ISO 8601 in Kyiv time, second precision."""
    if moment.tzinfo is None:
        raise ValueError("naive datetime")
    return moment.astimezone(KYIV).replace(microsecond=0).isoformat()


def today_kyiv(moment: datetime | None = None) -> date:
    return (moment or now_kyiv()).astimezone(KYIV).date()
