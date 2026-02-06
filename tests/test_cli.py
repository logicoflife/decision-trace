
import pytest
import json
from unittest.mock import patch, MagicMock
from io import StringIO
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
    assert "root (d1)" in out
    assert "child (d2)" in out
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
    assert "2023-01-01T00:00:00Z" in out
    # Check for event count summary
    assert "[1 events: start]" in out

def test_inspect_trace_not_found(mock_jsonl, capsys):
    with patch("sys.argv", ["decision-trace", "inspect", "nonexistent", "-f", str(mock_jsonl)]):
        main()
    captured = capsys.readouterr()
    assert "No events found for trace_id: nonexistent" in captured.out
