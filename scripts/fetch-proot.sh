#!/usr/bin/env bash
# Downloads proot and its dependencies from the Termux package repository
# and places them in jniLibs, so Android extracts them as executable files.
set -euo pipefail

REPO="https://packages.termux.dev/apt/termux-main"
ARCH="aarch64"
OUT="app/src/main/jniLibs/arm64-v8a"
WORK="$(mktemp -d)"

mkdir -p "$OUT"

echo "==> Downloading Termux package index"
INDEX_URL="$REPO/dists/stable/main/binary-$ARCH/Packages"
if ! curl -fsSL "$INDEX_URL" -o "$WORK/Packages"; then
  curl -fsSL "$INDEX_URL.gz" -o "$WORK/Packages.gz"
  gunzip -f "$WORK/Packages.gz"
fi

deb_path() {
  awk -v pkg="$1" '
    BEGIN { RS = ""; FS = "\n" }
    {
      name = ""; file = ""
      for (i = 1; i <= NF; i++) {
        if ($i ~ /^Package: /)  name = substr($i, 10)
        if ($i ~ /^Filename: /) file = substr($i, 11)
      }
      if (name == pkg) { print file; exit }
    }
  ' "$WORK/Packages"
}

fetch_deb() {
  local pkg="$1"
  local path
  path="$(deb_path "$pkg")"
  if [ -z "$path" ]; then
    echo "Package not found in index: $pkg" >&2
    return 1
  fi
  echo "==> $pkg -> $path"
  curl -fsSL "$REPO/$path" -o "$WORK/$pkg.deb"
  mkdir -p "$WORK/$pkg"
  dpkg-deb -x "$WORK/$pkg.deb" "$WORK/$pkg"
}

fetch_deb proot
fetch_deb libtalloc
fetch_deb libandroid-shmem || echo "WARN: libandroid-shmem not available"

PROOT_BIN="$(find "$WORK/proot" -type f -path '*/usr/bin/proot' | head -n 1 || true)"
LOADER="$(find "$WORK/proot" -type f -path '*/libexec/proot/loader' | head -n 1 || true)"
LOADER32="$(find "$WORK/proot" -type f -path '*/libexec/proot/loader32' | head -n 1 || true)"
TALLOC="$(find "$WORK/libtalloc" -type f -name 'libtalloc.so*' | head -n 1 || true)"
SHMEM="$(find "$WORK/libandroid-shmem" -type f -name 'libandroid-shmem.so*' 2>/dev/null | head -n 1 || true)"

if [ -z "$PROOT_BIN" ] || [ -z "$LOADER" ] || [ -z "$TALLOC" ]; then
  echo "ERROR: proot, loader or talloc not found in the Termux packages" >&2
  exit 1
fi

install -m 0755 "$PROOT_BIN" "$OUT/libproot.so"
install -m 0755 "$LOADER" "$OUT/libproot-loader.so"
install -m 0755 "$TALLOC" "$OUT/libtalloc.so"
if [ -n "$LOADER32" ]; then
  install -m 0755 "$LOADER32" "$OUT/libproot-loader32.so"
fi
if [ -n "$SHMEM" ]; then
  install -m 0755 "$SHMEM" "$OUT/libandroid-shmem.so"
fi

echo "==> Installed files"
ls -l "$OUT"

echo "==> ELF report (dependencies and LOAD segment alignment)"
for f in "$OUT"/*.so; do
  echo "--- $(basename "$f")"
  readelf -d "$f" 2>/dev/null | grep -E 'NEEDED|RUNPATH|RPATH' || true
  readelf -lW "$f" 2>/dev/null | awk '$1 == "LOAD" { print "LOAD align: " $NF }' || true
done
