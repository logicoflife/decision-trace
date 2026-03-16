import json
import sys

from .base import Exporter
from ..model import DecisionTraceEvent


class ConsoleExporter:
    def export(self, event: DecisionTraceEvent) -> None:
        payload = json.dumps(event.model_dump(), separators=(",", ":"))
        print(payload, file=sys.stdout)

    def flush(self) -> None:
        sys.stdout.flush()
