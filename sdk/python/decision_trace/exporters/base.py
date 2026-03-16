from typing import Protocol

from ..model import DecisionTraceEvent


class Exporter(Protocol):
    def export(self, event: DecisionTraceEvent) -> None:
        ...

    def flush(self) -> None:
        ...
