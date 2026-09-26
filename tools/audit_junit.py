#!/usr/bin/env python3
"""Read-only Gradle JUnit XML audit. No third-party dependencies.

Checks count consistency and explicitly approved cross-flavor skips. Does NOT
prove tests ran, test adequacy, screenshot authenticity, or APK provenance.
Input layout: XML_ROOT/<variant>/*.xml. Never edits any input report.
"""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
from typing import Any
import xml.etree.ElementTree as ET

FIELDS = ("total", "passed", "failed", "errors", "skipped")

def audit(xml_root: Path, skip_policy: list[dict[str, str]],
          handoff: dict[str, Any] | None = None) -> dict[str, Any]:
    xml_root = xml_root.resolve(strict=True)
    records: dict[tuple[str, str, str], dict[str, Any]] = {}
    by_variant: dict[str, dict[str, int]] = {}
    files: list[dict[str, str]] = []
    issues: list[str] = []
    paths = sorted(xml_root.glob("*/*.xml"))
    if not paths:
        raise ValueError("No <variant>/*.xml reports found")
    for path in paths:
        if path.is_symlink() or not path.resolve().is_relative_to(xml_root):
            raise ValueError(f"Unsupported symbolic link: {path.name}")
        if path.stat().st_size > 20_000_000:
            raise ValueError(f"XML too large: {path.name}")
        raw = path.read_bytes()
        if b"<!DOCTYPE" in raw.upper() or b"<!ENTITY" in raw.upper():
            raise ValueError("DTD/entity declarations are not allowed")
        root = ET.fromstring(raw)
        if root.tag != "testsuite":
            raise ValueError(f"Expected Gradle testsuite root: {path.name}")
        variant = path.parent.name
        actual = dict.fromkeys(FIELDS, 0)
        for case in root.findall("testcase"):
            classname, name = case.get("classname"), case.get("name")
            if not classname or not name:
                raise ValueError("Every testcase requires classname and name")
            states = [s for s in ("failure", "error", "skipped") if case.find(s) is not None]
            if len(states) > 1:
                raise ValueError(f"Ambiguous testcase outcome: {classname}.{name}")
            outcome = {"failure": "failed", "error": "errors", "skipped": "skipped"}.get(
                states[0] if states else "", "passed")
            key = (variant, classname, name)
            if key in records:
                raise ValueError(f"Duplicate case within variant: {key}")
            records[key] = {"variant": variant, "classname": classname, "name": name,
                            "outcome": outcome, "xml": path.relative_to(xml_root).as_posix()}
            actual["total"] += 1
            actual[outcome] += 1
        for attr, field in (("tests", "total"), ("failures", "failed"),
                            ("errors", "errors"), ("skipped", "skipped")):
            value = root.get(attr)
            if value is None or not value.isdecimal() or int(value) != actual[field]:
                raise ValueError(f"Header/case mismatch {path.name}: {attr}={value}, actual={actual[field]}")
        totals = by_variant.setdefault(variant, dict.fromkeys(FIELDS, 0))
        for field in FIELDS:
            totals[field] += actual[field]
        files.append({"path": path.relative_to(xml_root).as_posix(),
                      "sha256": hashlib.sha256(raw).hexdigest(),
                      "xml_timestamp": root.get("timestamp", "")})
    approved: dict[tuple[str, str, str], dict[str, str]] = {}
    for item in skip_policy:
        required = ("variant", "classname", "name", "reason", "covered_by_variant")
        if not all(isinstance(item.get(k), str) and item[k].strip() for k in required):
            raise ValueError("Skip policy requires variant/classname/name/reason/covered_by_variant")
        key = (item["variant"], item["classname"], item["name"])
        if key in approved:
            raise ValueError(f"Duplicate skip policy: {key}")
        approved[key] = item
        if key not in records or records[key]["outcome"] != "skipped":
            issues.append(f"Stale/unmatched skip policy: {key}")
    for key, record in records.items():
        if record["outcome"] != "skipped":
            continue
        item = approved.get(key)
        if item is None:
            issues.append(f"Unapproved skip: {key}")
            continue
        counterpart = (item["covered_by_variant"], key[1], key[2])
        if counterpart == key or records.get(counterpart, {}).get("outcome") != "passed":
            issues.append(f"Skipped case has no passing cross-variant counterpart: {key}")
        record["skip_reason"] = item["reason"]
        record["covered_by_variant"] = item["covered_by_variant"]
    totals = {field: sum(v[field] for v in by_variant.values()) for field in FIELDS}
    if totals["failed"] or totals["errors"]:
        issues.append("One or more cases failed or errored")
    if not totals["passed"]:
        issues.append("No passing executed cases")
    if handoff is not None:
        aggregate = [r for r in handoff.get("tests", [])
                     if str(r.get("id", "")).split("/", 1)[0] == "BASE-01"]
        if len(aggregate) != 1:
            issues.append("Expected exactly one BASE-01 aggregate record")
        else:
            for field in ("passed", "failed", "skipped"):
                if aggregate[0].get(field) != totals[field]:
                    issues.append(f"Handoff BASE-01 {field}={aggregate[0].get(field)!r}, XML={totals[field]}")
            if "errors" in aggregate[0] and aggregate[0]["errors"] != totals["errors"]:
                issues.append("Handoff BASE-01 errors disagrees with XML")
    return {"status": "XML_COUNTS_VALID" if not issues else "BLOCKED",
            "limitations": "Count audit only; not proof of execution, adequate tests, build identity, or release readiness.",
            "by_variant": by_variant, "totals": totals, "cases": list(records.values()),
            "input_files": files, "issues": issues}

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("xml_root", type=Path)
    parser.add_argument("--skip-policy", type=Path)
    parser.add_argument("--handoff", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        if args.output.resolve() == (args.handoff.resolve() if args.handoff else None):
            raise ValueError("Output must not overwrite the handoff")
        if args.output.resolve().is_relative_to(args.xml_root.resolve()):
            raise ValueError("Output must be outside the raw XML directory")
        policy = json.loads(args.skip_policy.read_text()) if args.skip_policy else []
        handoff = json.loads(args.handoff.read_text()) if args.handoff else None
        result = audit(args.xml_root, policy, handoff)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
        print(result["status"] + ": " + json.dumps(result["totals"]))
        for issue in result["issues"]:
            print("ERROR: " + issue)
        return 0 if not result["issues"] else 1
    except (OSError, ValueError, TypeError, KeyError, ET.ParseError) as exc:
        print("INVALID_EVIDENCE: " + str(exc))
        return 2

if __name__ == "__main__":
    raise SystemExit(main())
