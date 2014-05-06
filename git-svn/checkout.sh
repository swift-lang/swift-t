#!/usr/bin/env bash

SCRIPT_DIR=$(dirname $0)

subrepos="c-utils lb turbine stc"
devrepo="dev"
masterrepo="swift-t"
allrepos="$subrepos $devrepo $masterrepo"

EXM_SVN=https://svn.mcs.anl.gov/repos/exm/sfw
#GITHUB_ROOT=https://github.com/timarmstrong
GITHUB_ROOT=git@github.com:timarmstrong

for subrepo in $subrepos
do
  # include releases but not branches
  git svn clone --trunk=trunk --tags=release \
          --prefix="svn/" -A "$SCRIPT_DIR/svn-authors.txt" \
          "$EXM_SVN/$subrepo" "$subrepo" &

done

git svn clone "https://svn.mcs.anl.gov/repos/exm/sfw/dev" dev &

git clone "${GITHUB_ROOT}/$masterrepo.git" $masterrepo &

wait


for subrepo in $allrepos
do
  pushd $subrepo > /dev/null
  # Add remote for github repository
  git remote add github "$GITHUB_ROOT/$subrepo.git"
  popd > /dev/null
done
