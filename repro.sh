#!/bin/bash
# Set PYTHONPATH to include current dir and collector dir
export PYTHONPATH=$PYTHONPATH:$(pwd):$(pwd)/collector

# Check if .venv exists
if [ -d ".venv" ]; then
    PYTHON=".venv/bin/python"
    UVICORN=".venv/bin/uvicorn"
else
    PYTHON="python3"
    UVICORN="uvicorn"
fi

echo "Using python: $PYTHON"

# Start server in background
# We run uvicorn as a module to avoid path issues if possible, or use the direct binary
$PYTHON -m uvicorn decision_trace_collector.api:app --app-dir collector --port 8000 &
SERVER_PID=$!

echo "Server PID: $SERVER_PID"
echo "Waiting for server to start..."
sleep 5

# Run client
echo "Running client..."
$PYTHON repro_client.py
CLIENT_EXIT_CODE=$?

# Kill server
echo "Killing server..."
kill $SERVER_PID || true

exit $CLIENT_EXIT_CODE
