#!/usr/bin/env python3
"""Create a delta tar from two uncompressed tar files.

usage: make_delta2.py BASE.tar NEW.tar OUT.tar

OUT.tar contains every entry of NEW.tar that is missing from BASE.tar or that
differs from it (type, size, link target, mode, owner or modification time).
Changed files such as /etc/ld.so.cache or /var/lib/dpkg/status are therefore
included, not only brand new paths.

Hard links are written as regular files, so the delta never depends on a link
target that lives in another archive.  Deleted files are not tracked.
"""
import sys
import tarfile


def norm(name):
    n = name
    while n.startswith("./"):
        n = n[2:]
    return n.strip("/")


def describe(by_name, m):
    """Comparable description of an entry (hard links resolved to their target)."""
    if m.islnk():
        target = by_name.get(norm(m.linkname))
        if target is None:
            return ("L?", m.linkname)
        return (tarfile.REGTYPE, target.size, "", target.mode, m.uid, m.gid, int(m.mtime))
    if m.issym():
        return (m.type, 0, m.linkname, m.mode, m.uid, m.gid, int(m.mtime))
    if m.isreg():
        return (tarfile.REGTYPE, m.size, "", m.mode, m.uid, m.gid, int(m.mtime))
    return (m.type, 0, "", m.mode, m.uid, m.gid, int(m.mtime))


def index(tf):
    by_name = {}
    for m in tf.getmembers():
        by_name[norm(m.name)] = m
    return by_name


def main():
    if len(sys.argv) != 4:
        print(__doc__)
        return 2
    base_path, new_path, out_path = sys.argv[1:4]

    with tarfile.open(base_path, "r:") as base_tf:
        base_idx = index(base_tf)
        base_desc = {n: describe(base_idx, m) for n, m in base_idx.items()}

    kept = 0
    skipped = 0
    total_bytes = 0
    with tarfile.open(new_path, "r:") as new_tf, \
            tarfile.open(out_path, "w:", format=tarfile.GNU_FORMAT) as out:
        new_idx = index(new_tf)
        for m in new_tf.getmembers():
            name = norm(m.name)
            if not name:
                continue
            # tarfile keeps the last duplicate; skip earlier duplicates
            if new_idx.get(name) is not m:
                continue
            desc = describe(new_idx, m)
            if base_desc.get(name) == desc:
                skipped += 1
                continue

            if m.islnk():
                target = new_idx.get(norm(m.linkname))
                if target is None or not target.isreg():
                    continue
                info = tarfile.TarInfo(m.name)
                info.type = tarfile.REGTYPE
                info.size = target.size
                info.mode = target.mode
                info.uid = m.uid
                info.gid = m.gid
                info.uname = m.uname
                info.gname = m.gname
                info.mtime = m.mtime
                out.addfile(info, new_tf.extractfile(target))
                total_bytes += target.size
            elif m.isreg():
                out.addfile(m, new_tf.extractfile(m))
                total_bytes += m.size
            else:
                out.addfile(m)
            kept += 1

    print("delta: kept %d entries (%.1f MB of file data), unchanged %d" %
          (kept, total_bytes / 1048576.0, skipped))
    return 0


if __name__ == "__main__":
    sys.exit(main())
