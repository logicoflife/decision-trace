import json
from pathlib import Path
from typing import Iterable, Mapping, Any


class JsonlStorage:
    def __init__(self, file_path: Path) -> None:
        self._path = file_path
        self.ensure_ready()

    @property
    def path(self) -> Path:
        return self._path

    def ensure_ready(self) -> None:
        self._path.parent.mkdir(parents=True, exist_ok=True)
        with self._path.open("a", encoding="utf-8"):
            pass

    def append(self, events: Iterable[Mapping[str, Any]]) -> None:
        with self._path.open("a", encoding="utf-8") as handle:
            for event in events:
                payload = json.dumps(event, ensure_ascii=False, separators=(",", ":"))
                handle.write(f"{payload}\n")
            handle.flush()
