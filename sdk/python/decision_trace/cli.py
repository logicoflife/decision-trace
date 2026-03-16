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




def cmd_dev(args: argparse.Namespace) -> None:
    print(f"{BLUE}Decision Trace dev collector running{RESET}")
    print(f"Endpoint: {GREEN}http://127.0.0.1:8711/v1/events{RESET}")
    print(f"Ledger:   {GREEN}./data/events.jsonl{RESET}")
    print("Try:")
    print("  python sdk/python/examples/refund_workflow/run.py")
    print("  decision-trace inspect --last --verbose")
    
    # Ensure data directory exists
    Path("./data").mkdir(exist_ok=True)
    
    try:
        import uvicorn
        uvicorn.run(
            "decision_trace_collector.api:app",
            host="127.0.0.1",
            port=8711,
            reload=True,
            app_dir="services/collector",
        )
    except ImportError:
        print(f"{RED}Error: decision-trace-collector not installed. Install with [collector] extra.{RESET}")
        sys.exit(1)


def print_tree(
    events_by_id: Dict[str, dict],
    decision_id: str,
    prefix: str = "",
    is_last: bool = True,
    verbose: bool = False,
    all_events: Optional[List[dict]] = None,
    visual_children: Optional[Dict[str, List[str]]] = None,
) -> None:
    event = events_by_id.get(decision_id)
    if not event:
        print(f"{prefix}??? Missing decision {decision_id}")
        return

    # Basic info
    d_type = event.get("decision_type", "unknown")
    
    # Check for outcome
    outcome = "pending"
    if all_events:
        related = [e for e in all_events if e.get("decision_id") == decision_id]
        outcomes = [e for e in related if e.get("event_type") == "decision.outcome"]
        if outcomes:
            status = outcomes[0].get("payload", {}).get("status", "unknown")
            outcome = f"outcome={status}"
        else:
            errors = [e for e in related if e.get("event_type") == "decision.error"]
            if errors:
                outcome = f"{RED}error{RESET}"

    connector = "└── " if is_last else "├── "
    
    if not verbose:
        print(f"{prefix}{connector}{BLUE}{d_type}{RESET}  {outcome}")
    else:
        print(f"{prefix}{connector}{BLUE}{d_type}{RESET}")
        
        indent = prefix + ("    " if is_last else "│   ")
        
        if all_events:
            related = [e for e in all_events if e.get("decision_id") == decision_id]
            related.sort(key=lambda x: x.get("timestamp", ""))
            
            for e in related:
                etype = e.get("event_type", "").replace("decision.", "")
                payload = e.get("payload", {})
                
                if etype == "start":
                    actor = e.get("actor", {})
                    # Handle both Actor object (dict) and raw dict
                    atype = actor.get("type", "unknown")
                    aid = actor.get("id", "unknown")
                    print(f"{indent}start  actor={atype}:{aid}")
                    # Causal links
                    links = e.get("causal_links", [])
                    if links:
                        print(f"{indent}causal_links:")
                        for link in links:
                            ltype = link.get("type", "linked_to")
                            target = link.get("target_decision_id", "unknown")
                            print(f"{indent}  {ltype} → {target}")
                            
                elif etype == "evidence":
                    key = payload.get("key", "")
                    val = payload.get("value", "")
                    print(f"{indent}evidence {key}={val}")
                    
                elif etype == "policy_check":
                    policy = payload.get("policy", "")
                    result = payload.get("result", "")
                    print(f"{indent}policy {policy}: {result}")
                    
                elif etype == "approval":
                    print(f"{indent}approval {payload}")
                    
                elif etype == "outcome":
                    print(f"{indent}outcome {payload.get('status')}")
                    
                elif etype == "error":
                    print(f"{indent}{RED}error {payload.get('message')}{RESET}")
                    
                elif etype == "action":
                     print(f"{indent}action {payload}")
    
    # Find children
    if visual_children:
        children_ids = visual_children.get(decision_id, [])
        # We need to look up the event to sort, if possible, or just trust list order
        # Assuming visual_children construction appended in some order.
        # Let's sort children by timestamp of their start event for determinstic output
        children_events = [events_by_id[cid] for cid in children_ids if cid in events_by_id]
        children_events.sort(key=lambda x: x.get("timestamp", ""))
        children = [e["decision_id"] for e in children_events]
    else:
        # Fallback to strict parents only
        children_events = [
            e for e in events_by_id.values() 
            if e.get("event_type") == "decision.start" 
            and e.get("parent_decision_id") == decision_id
        ]
        children_events.sort(key=lambda x: x.get("timestamp", ""))
        children = [e["decision_id"] for e in children_events]
    
    child_prefix = prefix + ("    " if is_last else "│   ")
    for i, child_id in enumerate(children):
        print_tree(
            events_by_id, 
            child_id, 
            child_prefix, 
            i == len(children) - 1,
            verbose,
            all_events,
            visual_children
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

    # Handle --last resolution
    target_trace_id = args.trace_id
    if args.last:
        latest_ts = ""
        latest_tid = None
        
        for p in candidate_files:
            if not p.exists(): continue
            try:
                with p.open("r", encoding="utf-8") as f:
                    for line in f:
                        if not line.strip(): continue
                        try:
                            evt = json.loads(line)
                            if evt.get("event_type") == "decision.start":
                                ts = evt.get("timestamp", "")
                                if ts >= latest_ts:
                                    latest_ts = ts
                                    latest_tid = evt.get("trace_id")
                        except: pass
            except: pass
            
        if latest_tid:
            target_trace_id = latest_tid
        else:
            print(f"{RED}No traces found to inspect.{RESET}")
            return
            
    if not target_trace_id:
        print(f"{RED}Error: trace_id required unless --last is used.{RESET}")
        return

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
                        if event.get("trace_id") == target_trace_id:
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
        print(f"No events found for trace_id: {target_trace_id}")
        if not args.file:
             print(f"Checked: {checked}")
        return

    print(f"Trace: {GREEN}{target_trace_id}{RESET}")
    print(f"File:  {source_file}")
    print(f"Events: {len(found_events)}")
    print("-" * 40)

    # Index by decision_id (only start events for the tree structure)
    start_events = {
        e["decision_id"]: e 
        for e in found_events 
        if e.get("event_type") == "decision.start"
    }

    # Build visual hierarchy
    # We want to represent causal chains (A -> B -> C) as nested trees for readability
    # strict parenting (parent_decision_id) takes precedence, then causal links.
    
    visual_children: Dict[str, List[str]] = {d_id: [] for d_id in start_events}
    visual_parents = set()

    # 1. Strict parents
    for d_id, e in start_events.items():
        pid = e.get("parent_decision_id")
        if pid and pid in start_events:
            visual_children[pid].append(d_id)
            visual_parents.add(d_id)
            continue
            
        # 2. Causal links (only if no strict parent)
        # We greedily attach to the first valid target we find to form a chain
        links = e.get("causal_links")
        if links:
            for link in links:
                target = link.get("target_decision_id")
                ltype = link.get("type")
                # Visual nesting for flow-like relationships
                if target in start_events and ltype in ("depends_on", "triggered_by", "reviews", "authorized_by"):
                    visual_children[target].append(d_id)
                    visual_parents.add(d_id)
                    break # Attach to just one parent to keep it a tree

    # Find roots (nodes with no visual parent)
    roots = []
    for d_id, e in start_events.items():
        if d_id not in visual_parents:
            roots.append(e)
            
    # Sort roots by timestamp
    roots.sort(key=lambda x: x.get("timestamp", ""))

    for root in roots:
        print_tree(start_events, root["decision_id"], verbose=args.verbose, all_events=found_events, visual_children=visual_children)


from .linter import ContractValidator

def cmd_lint_contract(args: argparse.Namespace) -> None:
    contract_path = Path(args.contract)
    if not contract_path.exists():
        print(f"{RED}Error: Contract file {contract_path} not found.{RESET}")
        sys.exit(1)
        
    try:
        validator = ContractValidator(contract_path)
    except Exception as e:
        print(f"{RED}Error loading contract: {e}{RESET}")
        sys.exit(1)
        
    # Resolve events file
    jsonl_path = Path(args.file) if args.file else None
    
    if not jsonl_path:
        for p in [Path("./decision-trace.jsonl"), Path("./data/events.jsonl")]:
            if p.exists():
                jsonl_path = p
                break
                
    if not jsonl_path or not jsonl_path.exists():
        print(f"{RED}Error: No events file found.{RESET}")
        sys.exit(1)
        
    print(f"Linting {jsonl_path} against {contract_path} ...")
    
    events = []
    try:
        with jsonl_path.open("r", encoding="utf-8") as f:
            for line in f:
                if line.strip():
                    try:
                        events.append(json.loads(line))
                    except json.JSONDecodeError:
                        pass
    except Exception as e:
        print(f"{RED}Error reading events: {e}{RESET}")
        sys.exit(1)
        
    violations = validator.validate_stream(events)
    
    if violations:
        print(f"{RED}Found {len(violations)} violations:{RESET}")
        for v in violations:
            print(f"  {v}")
        # Warn-only for V1, or exit? 
        # BUILD_LOG says: "Warn-only mode in v1."
        print(f"{RED}Lint failed (Warn-only).{RESET}")
    else:
        print(f"{GREEN}No violations found. Contract satisfied.{RESET}")


def cmd_version(args: argparse.Namespace) -> None:
    print(f"Decision Trace v{SCHEMA_VERSION}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Decision Trace CLI")
    subparsers = parser.add_subparsers(dest="command", required=True)

    # inspect
    inspect_parser = subparsers.add_parser("inspect", help="Visualize a decision trace")
    inspect_parser.add_argument("trace_id", nargs="?", help="The trace_id to inspect")
    inspect_parser.add_argument("--last", action="store_true", help="Inspect latest trace")
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
    
    # lint-contract
    lint_parser = subparsers.add_parser("lint-contract", help="Validate events against a contract")
    lint_parser.add_argument("contract", help="Path to contract.yaml")
    lint_parser.add_argument(
        "-f", "--file",
        required=False,
        help="Path to JSONL file to lint"
    )
    lint_parser.set_defaults(func=cmd_lint_contract)

    # dev
    dev_parser = subparsers.add_parser("dev", help="Start local collector")
    dev_parser.set_defaults(func=cmd_dev)
    
    # version
    version_parser = subparsers.add_parser("version", help="Show version")
    version_parser.set_defaults(func=cmd_version)

    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
