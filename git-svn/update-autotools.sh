#!/usr/bin/env bash
autotools_dirs="c-utils lb/code turbine/code"

for dir in $autotools_dirs; do
  echo "Entering $dir"
  pushd $dir > /dev/null
  git checkout master 
  git branch -D __autotools_update &> /dev/null
  git checkout -b __autotools_update github/master
  if git diff-index --quiet HEAD --; then
    ./setup.sh
    git commit -a -m "Regenerate build scripts"
  else
    echo "Not updating scripts: uncommitted changes"
  fi
  popd > /dev/null
done
