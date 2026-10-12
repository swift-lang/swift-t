#!/bin/zsh -f
set -eu

# ANACONDA-LIST-FILES.SH
# Print a table of PACKAGE / PLATFORM / VERSION / BUILD for files published
# under the 'swift-t' organization on anaconda.org, via the anaconda.org
# REST API.  Useful for picking the next BUILD_NUMBER to use.
#
# usage: ./anaconda-list-files.sh [PACKAGE]
#   With no PACKAGE: tables every package under the swift-t org.
#   With PACKAGE:    tables just that package's files (e.g. swift-t-r).

ORG="swift-t"
API="https://api.anaconda.org"

table()
# Read a JSON file list (API /package/.../files) on stdin,
# print PACKAGE/PLATFORM/VERSION/BUILD, sorted.
{
  python3 -c '
import json, sys
data = json.load(sys.stdin)
rows = []
for f in data:
    a = f.get("attrs", {}) or {}
    rows.append((
        f.get("full_name", "").split("/")[1],
        a.get("subdir", ""),
        f.get("version", ""),
        a.get("build", ""),
    ))
rows.sort()
fmt = "{:<20} {:<15} {:<12} {}"
print(fmt.format("PACKAGE", "PLATFORM", "VERSION", "BUILD"))
for r in rows:
    print(fmt.format(*r))
'
}

if (( ${#*} == 0 ))
then
  for PACKAGE in $( curl -fsSL "${API}/packages/${ORG}" | python3 -c '
import json, sys
for p in json.load(sys.stdin):
    print(p["name"])
' )
  do
    curl -fsSL "${API}/package/${ORG}/${PACKAGE}/files" | table
    print
  done
else
  PACKAGE=$1
  curl -fsSL "${API}/package/${ORG}/${PACKAGE}/files" | table
fi
