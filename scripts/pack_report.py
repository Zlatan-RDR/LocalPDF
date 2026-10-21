import collections
import hashlib
import json
import os
import sys
import tarfile

MB = 1024 * 1024
RELEASE_ASSET_LIMIT = 2 * 1024 * 1024 * 1024


def sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(MB), b""):
            digest.update(chunk)
    return digest.hexdigest()


def check_limit(path):
    size = os.path.getsize(path)
    if size >= RELEASE_ASSET_LIMIT:
        raise SystemExit(f"{path} is {size} bytes, above the 2 GiB release asset limit")
    return size


def analyse_tar(path):
    installed = 0
    by_dir = collections.Counter()
    with tarfile.open(path) as tf:
        for member in tf:
            if not member.isfile():
                continue
            installed += member.size
            parts = os.path.normpath(member.name).split("/")
            by_dir["/" + "/".join(parts[:3])] += member.size
    return installed, by_dir


def main():
    if len(sys.argv) < 4:
        raise SystemExit("usage: pack_report.py OUT_DIR STIRLING_TAG PACK [PACK...]")

    out_dir = sys.argv[1]
    stirling_tag = sys.argv[2]
    packs = sys.argv[3:]

    manifest = {"stirling_tag": stirling_tag, "files": {}}
    summary = [
        "## Size report",
        "",
        f"Stirling-PDF release: {stirling_tag}",
        "",
        "| File | Download (MB) | Installed (MB) |",
        "|---|---|---|",
    ]
    details = []

    for name in packs:
        tar_path = os.path.join(out_dir, name + ".tar")
        xz_path = tar_path + ".xz"
        installed, by_dir = analyse_tar(tar_path)
        size = check_limit(xz_path)
        manifest["files"][name + ".tar.xz"] = {
            "bytes": size,
            "installed_bytes": installed,
            "sha256": sha256(xz_path),
        }
        summary.append(f"| {name}.tar.xz | {size / MB:.1f} | {installed / MB:.1f} |")
        details.append("")
        details.append(f"### Largest directories in {name}")
        details.append("")
        details.append("| Path | MB |")
        details.append("|---|---|")
        for key, value in by_dir.most_common(15):
            details.append(f"| {key} | {value / MB:.1f} |")

    jar_path = os.path.join(out_dir, "stirling-server.jar")
    jar_size = check_limit(jar_path)
    manifest["files"]["stirling-server.jar"] = {
        "bytes": jar_size,
        "installed_bytes": jar_size,
        "sha256": sha256(jar_path),
    }
    summary.append(f"| stirling-server.jar | {jar_size / MB:.1f} | {jar_size / MB:.1f} |")

    total_download = sum(v["bytes"] for v in manifest["files"].values())
    total_installed = sum(v["installed_bytes"] for v in manifest["files"].values())
    summary.append(f"| **Total** | {total_download / MB:.1f} | {total_installed / MB:.1f} |")

    with open(os.path.join(out_dir, "manifest.json"), "w") as f:
        json.dump(manifest, f, indent=2)

    text = "\n".join(summary + details) + "\n"
    print(text)

    summary_file = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_file:
        with open(summary_file, "a") as f:
            f.write(text)


if __name__ == "__main__":
    main()
