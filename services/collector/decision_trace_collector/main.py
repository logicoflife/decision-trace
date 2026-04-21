import sys

def main() -> None:
    try:
        import uvicorn
        from .api import create_app
    except ImportError:
        print(
            'decision-trace-collector requires the "collector" extra. '
            'Install with: pip install "decision-trace[collector]"',
            file=sys.stderr,
        )
        raise SystemExit(1)

    try:
        app = create_app()
    except RuntimeError as exc:
        print(f"decision-trace-collector failed to start: {exc}", file=sys.stderr)
        raise SystemExit(1)

    uvicorn.run(app, host="0.0.0.0", port=8000)


if __name__ == "__main__":
    main()
