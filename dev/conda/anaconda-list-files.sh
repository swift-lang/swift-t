#!/bin/zsh -f
set -eu

# ANACONDA-LIST-FILES.SH
# Print a table of PACKAGE / PLATFORM / VERSION / BUILD / DATE for files
# published under the 'swift-t' organization on anaconda.org, via the
# anaconda.org REST API.  Useful for picking the next BUILD_NUMBER to use.
#
# usage: ./anaconda-list-files.sh [-p PLATFORM] [PACKAGE] [VERSION]
#   With no PACKAGE: tables every package under the swift-t org.
#   With PACKAGE:    tables just that package's files (e.g. swift-t-r).
#   With VERSION:    further restrict to that version (e.g. 1.6.9).
#   -p PLATFORM:     restrict to that platform/subdir (e.g. linux-64).

ORG="swift-t"
API="https://api.anaconda.org"

help()
{
  cat <<EOF
USAGE: anaconda-list-files.sh [-p PLATFORM] [PACKAGE] [VERSION]
       anaconda-list-files.sh -h

Print a table of PACKAGE / PLATFORM / VERSION / BUILD / DATE for files
published under the 'swift-t' organization on anaconda.org.  Useful for
picking the next BUILD_NUMBER to use.

  (no args)       Table every package under the swift-t org.
  PACKAGE         Table just that package's files (e.g. swift-t-r).
  VERSION         Further restrict to that version (e.g. 1.6.9).
                  Only valid together with PACKAGE.
  -p PLATFORM     Restrict to that platform/subdir (e.g. linux-64).
  -h              Show this help and exit.

EXAMPLES:
  anaconda-list-files.sh
  anaconda-list-files.sh swift-t-r
  anaconda-list-files.sh -p linux-64 swift-t-r 1.6.9
EOF
}

zparseopts -D -E h=H p:=P

if (( ${#H} )) { help ; exit 0 }

PLATFORM=""
if (( ${#P} )) PLATFORM=${P[2]}

list_packages()
# Print the name of every package under the org, one per line
{
  curl -fsSL "${API}/packages/${ORG}" | jq -r '.[].name'
}

table()
# Read a JSON file list (API /package/.../files) on stdin,
# print PACKAGE/PLATFORM/VERSION/BUILD/DATE, sorted, filtered by
# PLATFORM/VERSION (both optional, from the enclosing shell).
# DATE is the upload_time, truncated to YYYY-MM-DD.
{
  {
    printf "PACKAGE\tPLATFORM\tVERSION\tBUILD\tDATE\n"
    jq -r --arg platform "${PLATFORM}" --arg version "${VERSION:-}" '
      .[]
      | select($platform == "" or .attrs.subdir  == $platform)
      | select($version  == "" or .version       == $version)
      | [(.full_name | split("/")[1]), .attrs.subdir, .version, .attrs.build,
         (.upload_time | split(" ")[0])]
      | @tsv
    ' | sort
  } | column -t -s $'\t'
}

if (( ${#*} == 0 ))
then
  PACKAGES=( $( list_packages ) )
  for PACKAGE in ${PACKAGES}
  do
    curl -fsSL "${API}/package/${ORG}/${PACKAGE}/files" | table
    print
  done
else
  PACKAGE=$1
  VERSION=${2:-}
  curl -fsSL "${API}/package/${ORG}/${PACKAGE}/files" | table
fi
