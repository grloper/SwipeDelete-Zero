#!/usr/bin/env python3
"""Serialize heavy jobs without waiting on the signing job that needs this runtime."""
import argparse
import json
import re
import subprocess
import time


def choose_run(runs, sha):
    exact = [run for run in runs if run.get("head_sha") == sha]
    return max(exact, key=lambda run: (run["run_number"], run.get("run_attempt", 1))) if exact else None


def verify_status(jobs):
    matches = [job for job in jobs if job.get("name") == "verify"]
    if len(matches) != 1 or matches[0].get("status") != "completed":
        return "pending"
    return "success" if matches[0].get("conclusion") == "success" else "failed"


def api(endpoint):
    result = subprocess.run(["gh", "api", endpoint], check=True, capture_output=True, text=True, timeout=45)
    return json.loads(result.stdout)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("sha")
    parser.add_argument("--repository", required=True)
    args = parser.parse_args()
    if not re.fullmatch(r"[0-9a-f]{40}", args.sha) or not re.fullmatch(r"[\w.-]+/[\w.-]+", args.repository):
        raise SystemExit("Explicit exact SHA and repository required")
    deadline = time.monotonic() + 3600
    while time.monotonic() < deadline:
        runs = api(f"repos/{args.repository}/actions/workflows/play.yml/runs?head_sha={args.sha}&per_page=100")["workflow_runs"]
        run = choose_run(runs, args.sha)
        if run:
            jobs = api(f"repos/{args.repository}/actions/runs/{run['id']}/attempts/{run.get('run_attempt', 1)}/jobs?per_page=100")["jobs"]
            status = verify_status(jobs)
            if status == "success":
                print(f"Exact-head verify job passed; heavy runtime slot released: {args.sha}")
                return
            if status == "failed":
                raise SystemExit(f"Exact-head verify failed; runtime not started: {args.sha}")
        print(f"Waiting for exact-head verify job: {args.sha}", flush=True)
        time.sleep(20)
    raise SystemExit("Timed out waiting for exact-head verify; runtime not started")


if __name__ == "__main__":
    main()
