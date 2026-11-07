"""Shared HTTP client with a neutral User-Agent and polite defaults."""

from __future__ import annotations

import time

import httpx

VERSION = "0.1.0"
USER_AGENT = f"KolyYe/{VERSION} (+https://vkardin.github.io)"
TIMEOUT = httpx.Timeout(20.0, connect=10.0)


class ProviderError(Exception):
    """Raised when a source cannot be fetched or parsed."""


class NotPublished(ProviderError):
    """The source works but has no schedule for today yet. Not worth an alert."""


def get(url: str, *, accept: str = "*/*", attempts: int = 2) -> httpx.Response:
    last_error: Exception | None = None
    headers = {"User-Agent": USER_AGENT, "Accept": accept, "Accept-Language": "uk"}
    for attempt in range(attempts):
        try:
            with httpx.Client(timeout=TIMEOUT, headers=headers, follow_redirects=True) as client:
                response = client.get(url)
            if response.status_code >= 500 and attempt + 1 < attempts:
                time.sleep(3)
                continue
            response.raise_for_status()
            return response
        except httpx.HTTPError as error:
            last_error = error
            if attempt + 1 < attempts:
                time.sleep(3)
    raise ProviderError(f"GET {url} failed: {last_error}")
