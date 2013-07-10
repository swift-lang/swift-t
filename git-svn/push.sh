#!/usr/bin/env bash
for subrepo in c-utils lb turbine stc dev
do
  echo Pushing $subrepo        
  pushd $subrepo > /dev/null
  git checkout master
  git branch -D __tmp_master &> /dev/null
  git checkout -b __tmp_master master
  git pull --rebase github master
  git push --tags github __tmp_master:master
  git checkout master
  git branch -D __tmp_master
  popd > /dev/null
  echo DONE
done
set -e

