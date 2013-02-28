#!/usr/bin/env bash
for subrepo in c-utils lb turbine stc dev
do
  echo Pushing $subrepo        
  pushd $subrepo > /dev/null
  git push --tags github master:master
  popd > /dev/null
  echo DONE
done
set -e

