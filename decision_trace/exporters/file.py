import json
from pathlib import Path

from .base import Exporter
from ..model import DecisionTraceEvent


class FileJsonlExporter:
    def __init__(self, file_path: str = "./decision-trace.jsonl") -> None:
        self._path = Path(file_path)
        self._handle = self._path.open("a", encoding="utf-8")

    def export(self, event: DecisionTraceEvent) -> None:
        payload = json.dumps(event.model_dump(), ensure_ascii=False, separators=(",", ":"))
        self._handle.write(f"{payload}\n")
        self._handle.flush()

    def flush(self) -> None:
        self._handle.flush()
