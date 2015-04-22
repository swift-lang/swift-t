#!/usr/bin/env bash

set -e

SCRIPT_DIR=$(dirname $0)
source "${SCRIPT_DIR}/repos.sh"

ERRORS=0

for subrepo in $subrepos
do
  echo Pushing $subrepo        
  pushd $subrepo > /dev/null
  
  if ! git push -q --tags github HEAD:master
  then
    echo "Not all refs were pushed ok, check previous output"
    echo "This may be because there were no changes to push"
    echo "or because the wrong branch was checked out."
    ERRORS=1
  fi
  
  popd > /dev/null
  echo DONE
  echo
  echo
done
set -e

pushd swift-t > /dev/null
# update submodules to latest github revs
for subrepo in $subrepos
do
  echo "Updating submodule $subrepo"
  pushd $subrepo > /dev/null
  git checkout master
  git fetch origin
  git rebase origin/master
  popd > /dev/null
done

# Commit any submodule updates
if ! git diff-index --quiet HEAD --; then
  git commit -a -m "Update submodules to latest"
else
  echo "Submodules unchanged"
fi
git pull --rebase github master
git push github master
popd > /dev/null

if (( ERRORS ))
then
  echo "Previous errors, check earlier output"
  exit 1
else
  echo "Completed with no errors"
fi
