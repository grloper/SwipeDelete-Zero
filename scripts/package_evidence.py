#!/usr/bin/env python3
"""Script to package and verify the M0 evidence archive according to review 2 specifications."""
import os
import sys
import hashlib
import zipfile
import shutil
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
EVIDENCE_DIR = REPO_ROOT / "evidence"
ZIP_OUTPUT = REPO_ROOT / "evidence.zip"

def compute_sha256(filepath: Path) -> str:
    h = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()

def generate_manifest():
    print(f"Generating SHA256SUMS.txt for {EVIDENCE_DIR}...")
    manifest_entries = []
    
    for root, dirs, files in os.walk(EVIDENCE_DIR):
        dirs.sort()
        files.sort()
        for file in files:
            full_path = Path(root) / file
            rel_path = full_path.relative_to(EVIDENCE_DIR).as_posix()
            
            # Exclude SHA256SUMS.txt and any zip or temp files
            if rel_path in ("SHA256SUMS.txt", "evidence.zip") or rel_path.endswith(".tmp"):
                continue
                
            file_hash = compute_sha256(full_path)
            manifest_entries.append(f"{file_hash}  {rel_path}")
            
    manifest_path = EVIDENCE_DIR / "SHA256SUMS.txt"
    manifest_path.write_text("\n".join(manifest_entries) + "\n", encoding="utf-8")
    print(f"Wrote {len(manifest_entries)} entries to {manifest_path}")

def create_archive():
    print(f"Packaging {EVIDENCE_DIR} into {ZIP_OUTPUT}...")
    if ZIP_OUTPUT.exists():
        ZIP_OUTPUT.unlink()
        
    with zipfile.ZipFile(ZIP_OUTPUT, "w", zipfile.ZIP_DEFLATED) as zf:
        for root, dirs, files in os.walk(EVIDENCE_DIR):
            dirs.sort()
            files.sort()
            for file in files:
                full_path = Path(root) / file
                rel_path = full_path.relative_to(EVIDENCE_DIR).as_posix()
                zf.write(full_path, arcname=rel_path)
                
    zip_hash = compute_sha256(ZIP_OUTPUT)
    print(f"Created {ZIP_OUTPUT} ({ZIP_OUTPUT.stat().st_size} bytes)")
    print(f"evidence.zip SHA-256: {zip_hash}")
    return zip_hash

def verify_archive(temp_dir: Path):
    print(f"Verifying {ZIP_OUTPUT} by extracting to {temp_dir}...")
    if temp_dir.exists():
        shutil.rmtree(temp_dir)
    temp_dir.mkdir(parents=True)
    
    with zipfile.ZipFile(ZIP_OUTPUT, "r") as zf:
        zf.extractall(temp_dir)
        
    manifest_path = temp_dir / "SHA256SUMS.txt"
    if not manifest_path.exists():
        raise RuntimeError("SHA256SUMS.txt missing from archive!")
        
    lines = manifest_path.read_text(encoding="utf-8").strip().splitlines()
    for line in lines:
        if not line.strip():
            continue
        expected_hash, rel_path = line.split(maxsplit=1)
        target_file = temp_dir / rel_path
        if not target_file.exists():
            raise RuntimeError(f"Missing file in archive: {rel_path}")
        actual_hash = compute_sha256(target_file)
        if actual_hash.lower() != expected_hash.lower():
            raise RuntimeError(f"Hash mismatch for {rel_path}: expected {expected_hash}, got {actual_hash}")
            
    print(f"Successfully verified {len(lines)} files against SHA256SUMS.txt in {temp_dir}")

if __name__ == "__main__":
    generate_manifest()
    zip_sha = create_archive()
    temp_extract = REPO_ROOT / "temp_evidence_verify"
    try:
        verify_archive(temp_extract)
    finally:
        if temp_extract.exists():
            shutil.rmtree(temp_extract)
    print("Evidence package generation and verification completed successfully.")
