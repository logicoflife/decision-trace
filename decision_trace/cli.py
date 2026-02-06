#!/usr/bin/env python3
import argparse
import json
import sys
from pathlib import Path
from typing import Dict, List, Optional

from .version import SCHEMA_VERSION

RED = "\033[91m"
GREEN = "\033[92m"
BLUE = "\033[94m"
RESET = "\033[0m"


def print_tree(
    events_by_id: Dict[str, dict],
    decision_id: str,
    prefix: str = "",
    is_last: bool = True,
    verbose: bool = False,
    all_events: Optional[List[dict]] = None,
) -> None:
    event = events_by_id.get(decision_id)
    if not event:
        print(f"{prefix}??? Missing decision {decision_id}")
        return

    # Basic info
    d_type = event.get("decision_type", "unknown")
    
    connector = "└── " if is_last else "├── "
    
    if not verbose:
        print(f"{prefix}{connector}{BLUE}{d_type}{RESET} ({decision_id})")
    else:
        # Collect related events for this decision
        related = []
        if all_events:
            related = [e for e in all_events if e.get("decision_id") == decision_id]
            related.sort(key=lambda x: x.get("timestamp", ""))
        
        types = [e.get("event_type", "").replace("decision.", "") for e in related]
        types_str = ",".join(types)
        print(f"{prefix}{connector}{BLUE}{d_type}{RESET} ({decision_id}) [{len(related)} events: {types_str}]")
        
        detail_prefix = prefix + ("    " if is_last else "│   ")
        for e in related:
            etype = e.get("event_type", "").replace("decision.", "")
            ts = e.get("timestamp", "")
            payload = e.get("payload", {})
            
            # Format based on type
            details = ""
            if etype == "start":
                actor = e.get("actor", {})
                a_str = f"actor={actor.get('type')}:{actor.get('id')}"
                t_str = f"tenant={e.get('tenant_id')} env={e.get('environment')}"
                details = f"{a_str} {t_str}"
            elif etype == "evidence":
                key = payload.get("key", "")
                val = str(payload.get("value", ""))
                if len(val) > 20:
                    val = val[:17] + "..."
                details = f"{key}={val}"
            elif etype == "outcome":
                details = str(payload)
            elif etype == "policy_check":
                policy = payload.get("policy", "")
                result = payload.get("result", "")
                details = f"{policy}={result}"
            else:
                details = str(payload)
                
            print(f"{detail_prefix}{etype:<9} {ts} {details}")
    
    # Find children
    children = [
        e for e in events_by_id.values() 
        if e.get("event_type") == "decision.start" 
        and e.get("parent_decision_id") == decision_id
    ]
    
    child_prefix = prefix + ("    " if is_last else "│   ")
    for i, child in enumerate(children):
        print_tree(
            events_by_id, 
            child["decision_id"], 
            child_prefix, 
            i == len(children) - 1,
            verbose,
            all_events
        )


from .storage import EventIndex

def cmd_index(args: argparse.Namespace) -> None:
    # Resolve file
    jsonl_path = Path(args.file) if args.file else None
    
    if not jsonl_path:
        for p in [Path("./decision-trace.jsonl"), Path("./data/events.jsonl")]:
            if p.exists():
                jsonl_path = p
                break
                
    db_path = Path(args.db)
    
    if not jsonl_path or not jsonl_path.exists():
        print(f"{RED}Error: No events file found to index.{RESET}")
        sys.exit(1)
        
    print(f"Indexing {jsonl_path} -> {db_path} ...")
    
    try:
        with EventIndex(db_path) as idx:
            count = idx.index_file(jsonl_path)
            print(f"{GREEN}Indexed {count} events.{RESET}")
    except Exception as e:
        print(f"{RED}Error indexing: {e}{RESET}")
        sys.exit(1)


def cmd_inspect(args: argparse.Namespace) -> None:
    db_path = Path(args.db)
    
    # Determine which files to check
    candidate_files = []
    if args.file:
        candidate_files.append(Path(args.file))
    else:
        candidate_files = [
            Path("./decision-trace.jsonl"),
            Path("./data/events.jsonl")
        ]

    found_events = []
    source_file = None
    
    for jsonl_path in candidate_files:
        if not jsonl_path.exists():
            continue
            
        # 1. Try index if available
        if db_path.exists():
            try:
                with EventIndex(db_path) as idx:
                    # Note: We assume the index tracks offsets valid for this file.
                    # In a real system, we'd check if the index matches the file signature.
                    # primarily this works if the user indexed THIS file.
                    found_events = idx.get_trace_events(args.trace_id, jsonl_path)
            except Exception:
                pass
        
        if found_events:
            source_file = jsonl_path
            break
            
        # 2. Fallback to linear scan
        try:
            with jsonl_path.open("r", encoding="utf-8") as f:
                for line in f:
                    if not line.strip():
                        continue
                    try:
                        event = json.loads(line)
                        if event.get("trace_id") == args.trace_id:
                            found_events.append(event)
                    except json.JSONDecodeError:
                        continue
        except Exception:
            pass
            
        if found_events:
            source_file = jsonl_path
            break

    if not found_events:
        checked = ", ".join(str(p) for p in candidate_files)
        print(f"No events found for trace_id: {args.trace_id}")
        if not args.file:
             print(f"Checked: {checked}")
        return

    print(f"Trace: {GREEN}{args.trace_id}{RESET}")
    print(f"File:  {source_file}")
    print(f"Events: {len(found_events)}")
    print("-" * 40)

    # Index by decision_id (only start events for the tree structure)
    
    start_events = {
        e["decision_id"]: e 
        for e in found_events 
        if e.get("event_type") == "decision.start"
    }

    # Find root(s) - nodes with no parent in this trace context
    
    roots = []
    for d_id, e in start_events.items():
        parent_id = e.get("parent_decision_id")
        if not parent_id or parent_id not in start_events:
            roots.append(e)

    for root in roots:
        print_tree(start_events, root["decision_id"], verbose=args.verbose, all_events=found_events)


def cmd_version(args: argparse.Namespace) -> None:
    print(f"Decision Trace v{SCHEMA_VERSION}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Decision Trace CLI")
    subparsers = parser.add_subparsers(dest="command", required=True)

    # inspect
    inspect_parser = subparsers.add_parser("inspect", help="Visualize a decision trace")
    inspect_parser.add_argument("trace_id", help="The trace_id to inspect")
    inspect_parser.add_argument(
        "-f", "--file", 
        default=None,
        help="Path to JSONL file (default: auto-discover)"
    )
    inspect_parser.add_argument(
        "-v", "--verbose",
        action="store_true",
        help="Show detailed event information"
    )
    inspect_parser.add_argument(
        "--db",
        default="./decision-trace.db",
        help="Path to SQLite index (default: ./decision-trace.db)"
    )
    inspect_parser.set_defaults(func=cmd_inspect)

    # index
    index_parser = subparsers.add_parser("index", help="Create an index for faster lookups")
    index_parser.add_argument(
        "-f", "--file",
        required=False,
        help="Path to JSONL file to index"
    )
    index_parser.add_argument(
        "--db",
        default="./decision-trace.db",
        help="Path to SQLite index (default: ./decision-trace.db)"
    )
    index_parser.set_defaults(func=cmd_index)

    # version
    version_parser = subparsers.add_parser("version", help="Show version")
    version_parser.set_defaults(func=cmd_version)

    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
