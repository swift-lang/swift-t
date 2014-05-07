#!/usr/bin/env bash

set -e

SCRIPT_DIR=$(dirname $0)
source "${SCRIPT_DIR}/repos.sh"

for dir in $autotools_dirs; do
  echo "Entering $dir"
  pushd $dir > /dev/null
  git checkout -q --detach
  if git branch -D __autotools_update &> /dev/null ; then
    echo "Removed old autotools temporary branch"
  fi
  git checkout -q -b __autotools_update github/master
  if git diff-index --quiet HEAD --; then
    ./setup.sh
    git commit -a -m "Regenerate build scripts"
  else
    echo "Not updating scripts: uncommitted changes"
  fi
  popd > /dev/null
done
