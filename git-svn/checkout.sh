#!/usr/bin/env bash

subrepos="c-utils lb turbine stc"
for subrepo in $subrepos
do
  git svn clone --stdlayout --tags=release \
          --prefix="svn/" \
          "https://svn.mcs.anl.gov/repos/exm/sfw/$subrepo" "$subrepo" &

done

git svn clone "https://svn.mcs.anl.gov/repos/exm/sfw/dev" dev &

wait

for subrepo in $subrepos
do
  pushd $subrepo > /dev/null
  # Add remote for github repository
  git remote add github git@github.com:timarmstrong/exm-$subrepo.git
  popd > /dev/null
done
