"""Builds normalized interval lists from per-minute ranges."""

from __future__ import annotations

from .models import Interval, IntervalType, minutes_to_hm

Span = tuple[int, int, IntervalType]


def build_intervals(spans: list[Span]) -> list[Interval]:
    """Sorts spans, merges touching spans of the same type and drops empty ones.

    Raises ValueError when spans of different types overlap.
    """
    cleaned = sorted((s, e, t) for s, e, t in spans if e > s)
    merged: list[list] = []
    for start, end, kind in cleaned:
        if not 0 <= start < end <= 1440:
            raise ValueError(f"span out of day range: {start}-{end}")
        if merged and start <= merged[-1][1]:
            last = merged[-1]
            if last[2] == kind:
                last[1] = max(last[1], end)
                continue
            if start < last[1]:
                raise ValueError(f"overlapping spans of different types at {start}")
        merged.append([start, end, kind])
    return [
        Interval(**{"from": minutes_to_hm(s), "to": minutes_to_hm(e), "type": t})
        for s, e, t in merged
    ]
