import json
from typing import List
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from .base import Exporter
from ..model import DecisionTraceEvent


class HttpExporter:
    def __init__(self, base_url: str = "http://localhost:8000") -> None:
        self._base_url = base_url.rstrip("/")
        self._buffer: List[dict] = []

    def export(self, event: DecisionTraceEvent) -> None:
        data = event.model_dump(mode="json", exclude_none=True)
        # parent_decision_id is required by schema, even if null.
        # exclude_none=True removes it, so we add it back if it was None.
        if "parent_decision_id" not in data:
            data["parent_decision_id"] = None
        self._buffer.append(data)

    def flush(self) -> None:
        if not self._buffer:
            return
        payload = json.dumps(self._buffer).encode("utf-8")
        request = Request(
            f"{self._base_url}/v1/events",
            data=payload,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        try:
            with urlopen(request) as response:
                if response.status >= 400:
                    body = response.read().decode("utf-8")
                    raise ValueError(f"Collector returned HTTP {response.status}: {body}")
        except HTTPError as exc:
            body = exc.read().decode("utf-8")
            raise ValueError(f"Collector returned HTTP {exc.code}: {body}") from exc
        except URLError as exc:
            raise RuntimeError(
                f"Collector unreachable at {self._base_url}. Is it running?"
            ) from exc
        self._buffer.clear()
