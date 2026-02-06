import sqlite3
import json
from pathlib import Path
from typing import List, Optional, Tuple

SCHEMA = """
CREATE TABLE IF NOT EXISTS events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    trace_id TEXT NOT NULL,
    decision_id TEXT NOT NULL,
    parent_decision_id TEXT,
    event_type TEXT NOT NULL,
    timestamp TEXT NOT NULL,
    file_offset INTEGER NOT NULL,
    line_number INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_trace_id ON events (trace_id);
CREATE INDEX IF NOT EXISTS idx_decision_id ON events (decision_id);
CREATE INDEX IF NOT EXISTS idx_parent_decision_id ON events (parent_decision_id);
"""

class EventIndex:
    def __init__(self, db_path: Path):
        self.db_path = db_path
        self._conn: Optional[sqlite3.Connection] = None

    def __enter__(self):
        self._conn = sqlite3.connect(self.db_path)
        self._conn.executescript(SCHEMA)
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        if self._conn:
            self._conn.close()
            self._conn = None

    def index_file(self, jsonl_path: Path) -> int:
        if not self._conn:
            raise RuntimeError("Database not connected")
        
        count = 0
        
        # We start a transaction for speed
        with self._conn:
            # Check existing max offset for this file? 
            # For V1, we naively re-index or just append. 
            # To be safe and simple: clear existing entries for this file? 
            # Actually, let's keep it simple: assume we are indexing a file content.
            # Real impl would track file path in DB.
            # For now, let's wipe existing index to prevent duplicates if user re-runs.
            self._conn.execute("DELETE FROM events")

            with jsonl_path.open("rb") as f:
                line_number = 0
                offset = 0
                while True:
                    line = f.readline()
                    if not line:
                        break
                    
                    line_len = len(line)
                    try:
                        event = json.loads(line)
                        self._conn.execute(
                            """
                            INSERT INTO events (
                                trace_id, decision_id, parent_decision_id, 
                                event_type, timestamp, file_offset, line_number
                            ) VALUES (?, ?, ?, ?, ?, ?, ?)
                            """,
                            (
                                event.get("trace_id"),
                                event.get("decision_id"),
                                event.get("parent_decision_id"),
                                event.get("event_type"),
                                event.get("timestamp"),
                                offset,
                                line_number
                            )
                        )
                        count += 1
                    except json.JSONDecodeError:
                        pass
                    
                    offset += line_len
                    line_number += 1
        return count

    def get_trace_events(self, trace_id: str, jsonl_path: Path) -> List[dict]:
        """
        Retrieves full event objects for a trace by looking up offsets 
        and reading from the file.
        """
        if not self._conn:
            raise RuntimeError("Database not connected")

        cursor = self._conn.execute(
            """
            SELECT file_offset FROM events 
            WHERE trace_id = ?
            ORDER BY id ASC
            """, 
            (trace_id,)
        )
        offsets = [row[0] for row in cursor.fetchall()]
        
        events = []
        if not offsets:
            return events

        with jsonl_path.open("rb") as f:
            for offset in offsets:
                f.seek(offset)
                line = f.readline()
                if line:
                    try:
                        events.append(json.loads(line))
                    except json.JSONDecodeError:
                        pass
        return events
