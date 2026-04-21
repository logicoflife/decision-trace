
import pytest
import json
from unittest.mock import patch, MagicMock
from pathlib import Path
from decision_trace.cli import main

@pytest.fixture
def mock_jsonl(tmp_path):
    f = tmp_path / "events.jsonl"
    events = [
        {
            "event_type": "decision.start",
            "trace_id": "t1",
            "decision_id": "d1",
            "parent_decision_id": None,
            "decision_type": "root",
            "timestamp": "2023-01-01T00:00:00Z"
        },
        {
            "event_type": "decision.start",
            "trace_id": "t1",
            "decision_id": "d2",
            "parent_decision_id": "d1",
            "decision_type": "child",
            "timestamp": "2023-01-01T00:00:01Z"
        },
        {
            "event_type": "decision.start",
            "trace_id": "t2", # Different trace
            "decision_id": "d3",
            "timestamp": "2023-01-01T00:00:02Z"
        }

    ]
    with f.open("w") as handle:
        for e in events:
            handle.write(json.dumps(e) + "\n")
    return f

def test_version_command(capsys):
    with patch("sys.argv", ["decision-trace", "version"]):
        main()
    captured = capsys.readouterr()
    assert "Decision Trace v" in captured.out


def test_dev_uses_env_configured_ledger_path(capsys, tmp_path, monkeypatch):
    ledger_path = tmp_path / "mounted" / "events.jsonl"
    monkeypatch.setenv("DECISION_TRACE_DATA_PATH", str(ledger_path))

    mock_uvicorn = MagicMock()
    with patch.dict(
        "sys.modules",
        {
            "uvicorn": mock_uvicorn,
            "decision_trace_collector": MagicMock(),
            "decision_trace_collector.api": MagicMock(),
        },
    ):
        with patch("sys.argv", ["decision-trace", "dev"]):
            main()

    captured = capsys.readouterr()
    assert str(ledger_path) in captured.out
    assert ledger_path.parent.exists()
    mock_uvicorn.run.assert_called_once()

def test_inspect_missing_file(capsys):
    with patch("sys.argv", ["decision-trace", "inspect", "t1", "-f", "nonexistent.jsonl"]):
        main()
    captured = capsys.readouterr()
    assert "No events found for trace_id: t1" in captured.out
    # It should not print "Checked:" because a file was explicitly provided
    assert "Checked:" not in captured.out

def test_inspect_trace_found(mock_jsonl, capsys):
    with patch("sys.argv", ["decision-trace", "inspect", "t1", "-f", str(mock_jsonl)]):
        main()
    captured = capsys.readouterr()
    import re
    ansi_escape = re.compile(r'\x1B(?:[@-Z\\-_]|\[[0-?]*[ -/]*[@-~])')
    out = ansi_escape.sub('', captured.out)
    assert "Trace: t1" in out
    assert "root  pending" in out
    assert "child  pending" in out
    assert "d3" not in out # Should not show other trace

def test_inspect_verbose(mock_jsonl, capsys):
    with patch("sys.argv", ["decision-trace", "inspect", "t1", "-f", str(mock_jsonl), "--verbose"]):
        main()
    captured = capsys.readouterr()
    import re
    ansi_escape = re.compile(r'\x1B(?:[@-Z\\-_]|\[[0-?]*[ -/]*[@-~])')
    out = ansi_escape.sub('', captured.out)
    
    # Check for verbose details
    assert "start" in out
    assert "root" in out
    assert "child" in out
    assert out.count("start  actor=unknown:unknown") == 2

def test_inspect_trace_not_found(mock_jsonl, capsys):
    with patch("sys.argv", ["decision-trace", "inspect", "nonexistent", "-f", str(mock_jsonl)]):
        main()
    captured = capsys.readouterr()
    assert "No events found for trace_id: nonexistent" in captured.out


@patch("decision_trace.cli.EventIndex")
def test_cmd_index(MockIndex, mock_jsonl, capsys):
    mock_db = MagicMock()
    mock_idx_instance = MagicMock()
    MockIndex.return_value.__enter__.return_value = mock_idx_instance
    mock_idx_instance.index_file.return_value = 10
    
    with patch("sys.argv", ["decision-trace", "index", "-f", str(mock_jsonl), "--db", "test.db"]):
        main()
        
    captured = capsys.readouterr()
    assert "Indexed 10 events." in captured.out
    MockIndex.assert_called_with(Path("test.db"))
    mock_idx_instance.index_file.assert_called_once()

@patch("decision_trace.cli.EventIndex")
def test_inspect_with_db(MockIndex, mock_jsonl, capsys):
    # Setup mock to simulate finding events via DB
    mock_idx_instance = MagicMock()
    MockIndex.return_value.__enter__.return_value = mock_idx_instance
    
    # Mock return of get_trace_events
    mock_idx_instance.get_trace_events.return_value = [
        {"trace_id": "t1", "decision_id": "d1", "event_type": "decision.start", "decision_type": "root", "timestamp": "2023-01-01"}
    ]
    
    # Since DB path needs to 'exist' for the CLI to use it, we need to mock Path.exists
    # But Path is used for many things. Let's just mock the 'exists' check for the db path specifically
    # Or cleaner: Just integration test with real DB in a temp dir if possible.
    # Given we are patching EventIndex, we can't easily rely on real DB existence check unless we create a real file.
    
    # Let's create a dummy file for the db check
    Path("test.db").touch()
    try:
        with patch("sys.argv", ["decision-trace", "inspect", "t1", "-f", str(mock_jsonl), "--db", "test.db"]):
            main()
    finally:
        Path("test.db").unlink(missing_ok=True)
            
    captured = capsys.readouterr()
    # Should find it via DB mock
    import re
    ansi = re.compile(r'\x1B(?:[@-Z\\-_]|\[[0-?]*[ -/]*[@-~])')
    out = ansi.sub('', captured.out)
    
    assert "Trace: t1" in out
    mock_idx_instance.get_trace_events.assert_called_once()

@patch("decision_trace.cli.EventIndex")
def test_index_auto_discovery(MockIndex, capsys, tmp_path):
    mock_idx_instance = MagicMock()
    MockIndex.return_value.__enter__.return_value = mock_idx_instance
    mock_idx_instance.index_file.return_value = 5

    # Simulate default file missing, but data/events.jsonl existing
    # We can't easily mock Path.exists for specific files without affecting others used by pytest/system
    # So we prefer to rely on the logic that checks paths. 
    # Let's mock the existence check via a side_effect on Path.exists if possible, or just create the file in CWD?
    # CLI uses relative paths "./data/events.jsonl", so we can change CWD or mock pathlib.Path behavior.
    
    # Simpler: Create the directory structure in a generic way or mock the list in the CLI?
    # CLI implementation has hardcoded paths: [Path("./decision-trace.jsonl"), Path("./data/events.jsonl")]
    # Let's mock Path.exists.
    
    with patch("pathlib.Path.exists") as mock_exists:
        # Define side effects: 
        # 1. Default file -> False
        # 2. Data file -> True
        # 3. DB file -> False (for the initial check, though it's created later)
        # Note: arg parsing creates Paths too.
        
        def exists_side_effect(self):
            s = str(self)
            if "decision-trace.jsonl" in s:
                return False
            if "data/events.jsonl" in s:
                return True
            return False # Default
            
        # This is too risky as it affects all Path objects.
        pass

    # Better approach: Integration test style with chdir?
    # Or just rely on the implementation being simple.
    # Let's verify logic by patching the list of candidates if we refactored valid candidates to a variable.
    # But I hardcoded it in the function.
    
    # Let's try changing cwd to tmp_path and creating the file there.
    d = tmp_path / "data"
    d.mkdir()
    f = d / "events.jsonl"
    f.touch()
    
    # We need to run the CLI in tmp_path
    import os
    cwd = os.getcwd()
    os.chdir(tmp_path)
    try:
        with patch("sys.argv", ["decision-trace", "index"]):
            main()
    finally:
        os.chdir(cwd)
        
    captured = capsys.readouterr()
    assert "Indexing data/events.jsonl" in captured.out
    MockIndex.assert_called()
