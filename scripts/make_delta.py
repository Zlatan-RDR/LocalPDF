import sys
import tarfile


def index(path):
    result = {}
    with tarfile.open(path) as tf:
        for member in tf:
            result[member.name] = (member.type, member.size, member.linkname)
    return result


def main():
    if len(sys.argv) != 4:
        raise SystemExit("usage: make_delta.py core.tar full.tar out.tar")

    core_path, full_path, out_path = sys.argv[1], sys.argv[2], sys.argv[3]
    core = index(core_path)

    added = 0
    added_bytes = 0

    with tarfile.open(full_path) as src, tarfile.open(out_path, "w") as dst:
        for member in src:
            key = (member.type, member.size, member.linkname)
            if core.get(member.name) == key:
                continue
            if member.isfile():
                fileobj = src.extractfile(member)
                dst.addfile(member, fileobj)
                added_bytes += member.size
            else:
                dst.addfile(member)
            added += 1

    print(f"Delta entries: {added}")
    print(f"Delta file bytes: {added_bytes}")


if __name__ == "__main__":
    main()
