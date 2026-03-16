from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]


def test_domain_first_repo_layout_exists():
    assert (ROOT / "sdk" / "python" / "decision_trace").is_dir()
    assert (ROOT / "sdk" / "java").is_dir()
    assert (ROOT / "services" / "collector" / "decision_trace_collector").is_dir()
    assert (ROOT / "contracts" / "decision-event.schema.yaml").is_file()
