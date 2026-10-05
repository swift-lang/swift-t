#!/usr/bin/env python3

"""
Print the dependency rules for a conda package file.

Reads info/index.json from the .conda package's info component and prints
the `depends` (run requirements) and `constrains` (optional pins) rules.

Usage:
    conda-deps.py PACKAGE.conda [PACKAGE2.conda ...]
    conda-deps.py --json PACKAGE.conda
"""

import io
import json
import os
import sys
import tarfile
import zipfile


def main():
    args = parse_args()
    for i, pkg in enumerate(args.packages):
        if i and not args.json:
            print()
        dump(pkg, as_json=args.json)

def parse_args():
    import argparse
    parser = argparse.ArgumentParser(
        description="Print dependency rules from a conda package file.")
    parser.add_argument("packages", nargs="+", metavar="PACKAGE",
                        help=".conda package file(s)")
    parser.add_argument("--json", action="store_true",
                        help="emit JSON instead of text")
    return parser.parse_args()


def zstd_decompress(data):
    """Decompress zstd bytes, trying the stdlib (3.14+), then the
    `zstandard` package, then the `zstd` command-line tool."""
    try:
        from compression import zstd  # Python 3.14+
        return zstd.decompress(data)
    except ImportError:
        pass
    try:
        import zstandard
        return zstandard.ZstdDecompressor().decompress(data)
    except ImportError:
        pass
    import shutil
    import subprocess
    if shutil.which("zstd") is None:
        sys.exit("error: need Python 3.14, the 'zstandard' package, "
                 "or the 'zstd' command-line tool to read .conda files")
    return subprocess.run(["zstd", "-dc"], input=data,
                          stdout=subprocess.PIPE, check=True).stdout


def index_from_tar(raw):
    """Extract and parse info/index.json from tar bytes."""
    with tarfile.open(fileobj=io.BytesIO(raw)) as tar:
        member = tar.extractfile("info/index.json")
        if member is None:
            return None
        return json.load(member)


def read_index(path):
    """Return the parsed index.json dict for a .conda package."""
    if not path.endswith(".conda"):
        sys.exit(f"error: not a .conda package: {path}")
    if not os.path.isfile(path):
        sys.exit(f"error: no such file: {path}")
    with zipfile.ZipFile(path) as z:
        # The metadata lives in the info-*.tar.zst component.
        info_names = [n for n in z.namelist()
                      if n.startswith("info-") and n.endswith(".tar.zst")]
        if not info_names:
            sys.exit(f"error: no info-*.tar.zst found in {path}")
        raw = zstd_decompress(z.read(info_names[0]))
        return index_from_tar(raw)


def dump(path, as_json=False):
    index = read_index(path)
    if index is None:
        sys.exit(f"error: no info/index.json in {path}")

    name = index.get("name", "?")
    version = index.get("version", "?")
    build = index.get("build", "?")
    depends = index.get("depends", []) or []
    constrains = index.get("constrains", []) or []

    if as_json:
        print(json.dumps({"name": name, "version": version, "build": build,
                          "depends": depends, "constrains": constrains},
                         indent=2))
        return

    print(f"{name} {version} ({build})")
    print(f"  depends ({len(depends)}):")
    for d in depends:
        print(f"    - {d}")
    if constrains:
        print(f"  constrains ({len(constrains)}):")
        for c in constrains:
            print(f"    - {c}")


if __name__ == "__main__":
    main()
