
import pytest
import sqlite3
import json
from pathlib import Path
from decision_trace.storage import EventIndex

@pytest.fixture
def db_path(tmp_path):
    return tmp_path / "test.db"

@pytest.fixture
def jsonl_path(tmp_path):
    f = tmp_path / "events.jsonl"
    events = [
        {"trace_id": "t1", "decision_id": "d1", "event_type": "decision.start", "timestamp": "2023-01-01", "payload": {}},
        {"trace_id": "t1", "decision_id": "d1", "event_type": "decision.outcome", "timestamp": "2023-01-02", "payload": {}},
        {"trace_id": "t2", "decision_id": "d2", "event_type": "decision.start", "timestamp": "2023-01-03", "payload": {}},
    ]
    with f.open("w") as handle:
        for e in events:
            handle.write(json.dumps(e) + "\n")
    return f

def test_index_creation(db_path, jsonl_path):
    with EventIndex(db_path) as idx:
        count = idx.index_file(jsonl_path)
        assert count == 3
        
    # Verify DB content
    conn = sqlite3.connect(db_path)
    cursor = conn.execute("SELECT count(*) FROM events")
    assert cursor.fetchone()[0] == 3
    
    cursor = conn.execute("SELECT trace_id FROM events WHERE trace_id='t1'")
    assert len(cursor.fetchall()) == 2
    conn.close()

def test_get_trace_events(db_path, jsonl_path):
    with EventIndex(db_path) as idx:
        idx.index_file(jsonl_path)
        
        events = idx.get_trace_events("t1", jsonl_path)
        assert len(events) == 2
        assert events[0]["trace_id"] == "t1"
        assert events[1]["event_type"] == "decision.outcome"
        
        events_t2 = idx.get_trace_events("t2", jsonl_path)
        assert len(events_t2) == 1
        assert events_t2[0]["trace_id"] == "t2"
        
        events_none = idx.get_trace_events("nonexistent", jsonl_path)
        assert len(events_none) == 0

def test_reindexing_clears_old(db_path, jsonl_path):
    with EventIndex(db_path) as idx:
        idx.index_file(jsonl_path)
        # Index same file again
        idx.index_file(jsonl_path)
        
    conn = sqlite3.connect(db_path)
    cursor = conn.execute("SELECT count(*) FROM events")
    assert cursor.fetchone()[0] == 3 # Should still be 3, not 6
    conn.close()
