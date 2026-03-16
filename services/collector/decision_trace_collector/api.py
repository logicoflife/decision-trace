from pathlib import Path
from typing import Any, Dict, List, Optional

from fastapi import Body, FastAPI
from fastapi.responses import JSONResponse

from .settings import DATA_PATH
from .storage import JsonlStorage
from .validate import validate_event


def create_app(data_path: Optional[Path] = None) -> FastAPI:
    app = FastAPI()
    storage = JsonlStorage(data_path or DATA_PATH)

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
