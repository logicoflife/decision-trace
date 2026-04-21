from decision_trace.validate import load_event_schema


def test_schema_resource_loads_from_package():
    schema = load_event_schema()

    assert schema["title"] == "Decision Trace Event v1.0"
    assert "tenant_id" in schema["required"]
