#!/usr/bin/env bash

SCRIPT_DIR=$(dirname $0)

subrepos="c-utils lb turbine stc"
for subrepo in $subrepos
do
  # include releases but not branches
  git svn clone --trunk=trunk --tags=release \
          --prefix="svn/" -A "$SCRIPT_DIR/svn-authors.txt" \
          "https://svn.mcs.anl.gov/repos/exm/sfw/$subrepo" "$subrepo" &

done

git svn clone "https://svn.mcs.anl.gov/repos/exm/sfw/dev" dev &

wait

for subrepo in $subrepos dev
do
  pushd $subrepo > /dev/null
  # Add remote for github repository
  git remote add github git@github.com:timarmstrong/exm-$subrepo.git
  popd > /dev/null
done
