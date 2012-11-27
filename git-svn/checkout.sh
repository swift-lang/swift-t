#!/usr/bin/env bash

for subrepo in c-utils lb turbine stc
do
  git svn clone --stdlayout --tags=release \
          --prefix="svn/" \
          "https://svn.mcs.anl.gov/repos/exm/sfw/$subrepo" "$subrepo" &
done

git svn clone "https://svn.mcs.anl.gov/repos/exm/sfw/dev" dev &

wait
