#!/usr/bin/env python3
"""Require completed successful verification workflows for the exact commit.

Read-only GitHub API requests via authenticated gh. Missing runs, API failures,
superseded failed attempts and timeout never authorize a publication.
"""
import argparse
import json
import re
import subprocess
import time

WORKFLOWS = ("play.yml", "emulator-smoke.yml")


def latest_success(runs, sha):
    candidates = [r for r in runs if r.get("head_sha") == sha]
    if not candidates:
        return False
    latest = max(candidates, key=lambda r: (r["run_number"], r.get("run_attempt", 1)))
    return latest.get("status") == "completed" and latest.get("conclusion") == "success"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("sha")
    parser.add_argument("--repository", required=True)
    parser.add_argument("--timeout", type=int, default=2700)
    parser.add_argument("--runtime-only", action="store_true", help="For Play bundle: verify job already passed via needs")
    args = parser.parse_args()
    if not re.fullmatch(r"[0-9a-f]{40}", args.sha):
        raise SystemExit("Gate requires an explicit full lowercase commit SHA")
    if not re.fullmatch(r"[\w.-]+/[\w.-]+", args.repository) or not 0 <= args.timeout <= 2700:
        raise SystemExit("Invalid repository or timeout")
    deadline = time.monotonic() + args.timeout
    while True:
        pending = []
        for workflow in (("emulator-smoke.yml",) if args.runtime_only else WORKFLOWS):
            endpoint = (f"repos/{args.repository}/actions/workflows/{workflow}/runs"
                        f"?head_sha={args.sha}&per_page=100")
            response = subprocess.run(["gh", "api", endpoint], check=True,
                                      capture_output=True, text=True, timeout=45)
            if not latest_success(json.loads(response.stdout)["workflow_runs"], args.sha):
                pending.append(workflow)
        if not pending:
            print(f"Exact-commit verification and synthetic runtime passed: {args.sha}")
            return
        if time.monotonic() >= deadline:
            raise SystemExit(f"Publication blocked: missing successful exact-SHA checks: {pending}")
        print(f"Waiting for exact-SHA checks: {pending}", flush=True)
        time.sleep(min(15, max(0, deadline - time.monotonic())))


if __name__ == "__main__":
    main()
