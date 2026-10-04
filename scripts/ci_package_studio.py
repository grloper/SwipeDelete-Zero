#!/usr/bin/env python3
"""Package actual screenshots separately from APKs; retain failure evidence too."""
import hashlib
import json
from pathlib import Path
import subprocess
import zipfile


def main():
    root = Path("evidence/studio")
    images = sorted(root.rglob("*.png"))
    if not images:
        raise SystemExit("No actual studio screenshots were captured")
    video = root / "interaction.mp4"
    validation = {"screenshots": len(images), "recording_decoded": False}
    error = None
    if video.exists():
        try:
            subprocess.run(["ffmpeg", "-nostdin", "-v", "error", "-xerror", "-threads", "1", "-i", str(video), "-map", "0:v:0", "-f", "null", "-"], check=True, timeout=120)
            info = json.loads(subprocess.check_output(["ffprobe", "-v", "error", "-show_entries", "format=duration:stream=width,height,codec_name", "-of", "json", str(video)], timeout=15))
            if float(info["format"]["duration"]) < 6:
                raise ValueError("Recording is too short to show the requested journey")
            validation.update(recording_decoded=True, recording=info)
        except (subprocess.SubprocessError, ValueError, KeyError) as exc:
            error = str(exc)
    else:
        error = "Independent interaction recording is missing"
    validation["error"] = error
    validation["files"] = {str(path.relative_to(root)): hashlib.sha256(path.read_bytes()).hexdigest() for path in sorted(root.rglob("*")) if path.is_file()}
    (root / "integrity.json").write_text(json.dumps(validation, indent=2) + "\n")
    output = Path("swipe-studio-review.zip")
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for path in sorted(root.rglob("*")):
            if path.is_file():
                archive.write(path, path.relative_to(root))
    if output.stat().st_size >= 32 * 1024 * 1024:
        raise SystemExit("Studio review archive exceeds the 32 MiB delivery limit")
    print(f"Review archive: {output.stat().st_size} bytes, {len(images)} actual screenshots")
    if error:
        raise SystemExit(error)


if __name__ == "__main__":
    main()
