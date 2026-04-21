from pathlib import Path
from typing import Any, Dict, List, Optional

from fastapi import Body, FastAPI
from fastapi.responses import JSONResponse

from .settings import resolve_data_path
from .storage import JsonlStorage
from .validate import SchemaUnavailableError, load_event_schema, validate_event


def _ensure_collector_ready(storage: JsonlStorage) -> None:
    try:
        load_event_schema()
    except SchemaUnavailableError as exc:
        raise RuntimeError(f"Collector startup failed: {exc}") from exc
    except Exception as exc:
        raise RuntimeError(f"Collector startup failed while loading schema: {exc}") from exc

    try:
        storage.ensure_ready()
    except OSError as exc:
        raise RuntimeError(
            f"Collector startup failed: output path '{storage.path}' is not writable."
        ) from exc


def create_app(data_path: Optional[Path] = None) -> FastAPI:
    storage = JsonlStorage(data_path or resolve_data_path())
    _ensure_collector_ready(storage)
    app = FastAPI()

    @app.post("/v1/events")
    def ingest_events(events: List[Dict[str, Any]] = Body(...)) -> JSONResponse:
        if not isinstance(events, list):
            return JSONResponse(status_code=400, content={"errors": [{"message": "Payload must be a list."}]})

        errors = []
        for index, event in enumerate(events):
            try:
                validate_event(event)
            except ValueError as exc:
                errors.append({"index": index, "message": str(exc)})

        if errors:
            return JSONResponse(status_code=400, content={"errors": errors})

        storage.append(events)
        return JSONResponse(status_code=200, content={"status": "ok", "count": len(events)})

    return app


app = create_app()
