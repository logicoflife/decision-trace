import os
from pathlib import Path

DATA_PATH_ENV_VAR = "DECISION_TRACE_DATA_PATH"
DEFAULT_DATA_PATH = Path("./data/events.jsonl")


def resolve_data_path() -> Path:
    value = os.getenv(DATA_PATH_ENV_VAR)
    return Path(value) if value else DEFAULT_DATA_PATH
