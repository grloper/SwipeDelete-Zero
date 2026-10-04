#!/usr/bin/env python3
"""Small, explicit runtime/validation archive; never includes install binaries.

This additional delivery artifact does not replace or weaken any existing gate.
Missing files are recorded, allowing useful diagnostics from failed CI runs.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import zipfile

CAP = 31 * 1024 * 1024
RUNTIME = (
    "dashboard.png", "instrumentation.txt", "window.xml", "collection.txt", "logcat.txt",
    "screenrecord.txt", "staged-kept-counts.txt", "journey-evidence/original-sha256.txt",
    *[f"journey-evidence/{name}.{suffix}" for name in
      ("cycle-0", "cycle-29", "cleanup-locked", "permission-denied", "permission-regranted")
      for suffix in ("png", "txt")],
    *[f"permission-{phase}-{cycle}.txt" for phase in ("denied", "granted") for cycle in (1, 2)],
)
VALIDATION = (
    "checkout.json", "build_identity.json", "handoff.json", "junit_audit.json", "HANDOFF.md",
    "SHA256SUMS.txt", "smoke.json", "media-fixtures.txt",
    "queue-viewport.json", "queue-scroll-trace.json",
    *[f"screens/{name}.{suffix}" for name in
      ("01-dashboard", "02-review", "03-staging", "04-lock-permanent", "04-lock-trash")
      for suffix in ("png", "xml")],
)


def package(kind, root, output, identity, cap=CAP):
    root = root.resolve(strict=True)
    if output.exists():
        raise ValueError("Refusing to reuse an existing compact archive")
    names = list(RUNTIME if kind == "runtime" else VALIDATION)
    if kind == "validation":
        for variant in ("play", "fdroid", "cloud"):
            names += [p.relative_to(root).as_posix() for p in
                      sorted((root / "test-results" / variant).glob("TEST-*.xml"))]
    selected, missing, total = [], [], 0
    for name in names:
        path = root / name
        if not path.exists():
            missing.append(name)
            continue
        if path.is_symlink() or not path.is_file() or not path.resolve().is_relative_to(root):
            raise ValueError(f"Unsafe evidence path: {name}")
        size = path.stat().st_size
        total += size
        # Reserve room for the manifest and ZIP headers before reading files.
        if total > cap - 65536:
            raise ValueError("Compact evidence exceeds transfer budget")
        raw = path.read_bytes()
        selected.append((name, raw))
    manifest = dict(kind=kind, identity=identity, missing=missing,
                    limitations="Selected CI evidence; inspect workflow outcome and full artifacts for validation.",
                    files=[dict(path=n, size_bytes=len(b), sha256=hashlib.sha256(b).hexdigest()) for n, b in selected])
    checkpoint = dict(selected).get("staged-kept-counts.txt")
    if checkpoint is not None:
        counts = checkpoint.decode().strip().splitlines()
        if len(counts) != 2 or not all(c.isdecimal() for c in counts):
            raise ValueError("Invalid observed staging checkpoint")
        manifest["observed_checkpoint"] = dict(staged=int(counts[0]), kept=int(counts[1]),
                                               source="debug app persisted journey checkpoint")
    with zipfile.ZipFile(output, "x", zipfile.ZIP_DEFLATED) as archive:
        for name, raw in selected:
            archive.writestr(name, raw)
        archive.writestr("COMPACT-MANIFEST.json", json.dumps(manifest, indent=2))
    if output.stat().st_size >= cap:
        output.unlink()
        raise ValueError("Compressed archive exceeds transfer budget")
    print(f"Compact {kind}: {len(selected)} files, {output.stat().st_size} bytes")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("kind", choices=("runtime", "validation"))
    parser.add_argument("root", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    identity = dict(checkout_sha=subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip(),
                    run_id=os.environ.get("GITHUB_RUN_ID"), run_attempt=os.environ.get("GITHUB_RUN_ATTEMPT"))
    package(args.kind, args.root, args.output, identity)
