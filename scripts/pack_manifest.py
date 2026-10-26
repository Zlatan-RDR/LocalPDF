#!/usr/bin/env python3
"""Write manifest.json for the runtime packs and print a size report.

usage: pack_manifest.py OUT_DIR STIRLING_TAG PACK [PACK ...]

For every PACK, OUT_DIR must contain PACK.tar (uncompressed, used for the
installed size) and PACK.tar.xz.  OUT_DIR must also contain stirling-server.jar.
The manifest layout is what Installer.kt expects:
  {"stirling_tag": ..., "files": {"core.tar.xz": {"bytes", "sha256", "installed_bytes"},
                                   "stirling-server.jar": {"bytes", "sha256"}}}
"""
import hashlib
import json
import os
import sys


def sha256_of(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        while True:
            chunk = f.read(1024 * 1024)
            if not chunk:
                break
            h.update(chunk)
    return h.hexdigest()


def main():
    if len(sys.argv) < 4:
        print(__doc__)
        return 2
    out_dir, tag = sys.argv[1], sys.argv[2]
    packs = sys.argv[3:]

    files = {}
    rows = []
    for name in packs:
        xz = os.path.join(out_dir, name + ".tar.xz")
        tar = os.path.join(out_dir, name + ".tar")
        for p in (xz, tar):
            if not os.path.isfile(p):
                print("missing file: " + p, file=sys.stderr)
                return 1
        entry = {
            "bytes": os.path.getsize(xz),
            "sha256": sha256_of(xz),
            "installed_bytes": os.path.getsize(tar),
        }
        files[name + ".tar.xz"] = entry
        rows.append((name, entry["bytes"], entry["installed_bytes"]))

    jar = os.path.join(out_dir, "stirling-server.jar")
    if not os.path.isfile(jar):
        print("missing file: " + jar, file=sys.stderr)
        return 1
    files["stirling-server.jar"] = {
        "bytes": os.path.getsize(jar),
        "sha256": sha256_of(jar),
    }
    rows.append(("stirling-server.jar", os.path.getsize(jar), 0))

    manifest = {"stirling_tag": tag, "files": files}
    with open(os.path.join(out_dir, "manifest.json"), "w") as f:
        json.dump(manifest, f, indent=2)

    lines = ["## Pack sizes", "", "| pack | download (MB) | installed (MB) |", "|---|---|---|"]
    for name, dl, inst in rows:
        lines.append("| %s | %.1f | %s |" % (name, dl / 1048576.0,
                                              ("%.1f" % (inst / 1048576.0)) if inst else "-"))
    report = "\n".join(lines) + "\n"
    print(report)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a") as f:
            f.write("\n" + report)
    return 0


if __name__ == "__main__":
    sys.exit(main())
