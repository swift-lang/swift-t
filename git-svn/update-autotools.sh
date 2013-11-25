#!/usr/bin/env bash
autotools_subrepos="c-utils lb turbine"

for subrepo in $autotools_subrepos; do
  if ! git diff-index --quiet HEAD --; then
    ./setup.sh
    git commit -a -m "Regenerate build scripts"
  else
    echo "Not updating scripts: uncommitted changes"
  fi
done
