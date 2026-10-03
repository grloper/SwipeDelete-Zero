#!/usr/bin/env python3
"""Fail closed unless mandatory runtime PNGs/video genuinely decode."""
import argparse
from pathlib import Path
import subprocess


def validate(root):
    required = [root / "dashboard.png", root / "review-journey.mp4"]
    journey = root / "journey-evidence"
    required += [journey / f"{name}.png" for name in
                 ("cycle-0", "cycle-29", "cleanup-locked", "permission-denied", "permission-regranted")]
    for path in required:
        if not path.is_file() or path.stat().st_size == 0:
            raise ValueError(f"Mandatory visual missing/empty: {path}")
        subprocess.run(["ffmpeg", "-nostdin", "-v", "error", "-xerror", "-i", str(path),
                        "-map", "0:v:0", "-f", "null", "-"], check=True, timeout=120)
        probe = subprocess.run(["ffprobe", "-v", "error", "-select_streams", "v:0",
                                "-show_entries", "stream=width,height", "-of", "csv=p=0",
                                str(path)], check=True, capture_output=True, text=True, timeout=15)
        dimensions = probe.stdout.strip().split(",")
        if len(dimensions) != 2 or any(int(d) <= 0 for d in dimensions):
            raise ValueError(f"No decodable video/image frame: {path}")
    print(f"Decoded {len(required)} mandatory runtime visuals")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("root", type=Path)
    validate(parser.parse_args().root)
