#!/usr/bin/env bash

subrepos="c-utils lb turbine stc dev"

for subrepo in $subrepos
do
  echo Pushing $subrepo        
  pushd $subrepo > /dev/null

  if [[ $subrepo == dev ]] ; then
    git_svn_remote="remotes/git-svn"
  else
    git_svn_remote="remotes/svn/trunk"
  fi

  git checkout master
  git branch -D __tmp_master &> /dev/null
  git fetch github
  git checkout -b __tmp_master remotes/github/master
  last_svn_rev=$(git log . | grep git-svn-id | head -n1 | grep -o '@[0-9]*' | sed 's/@/r/')
  echo "Last svn revision on github: $last_svn_rev"
  git_svn_head_hash=$(git rev-parse $git_svn_remote)
  last_merged_hash=$(git svn find-rev $last_svn_rev $git_svn_remote)
  echo "need to merge $last_merged_hash..$git_svn_head_hash from git-svn"
  if [[ "${git_svn_head_hash}" == "${last_merged_hash}" ]]; then
    echo "No commits to merge"
  else
    git cherry-pick --ff ${last_merged_hash}..${git_svn_head_hash}
    git push --tags github __tmp_master:master
  fi
  git checkout master
  git branch -D __tmp_master
  popd > /dev/null
  echo DONE
  echo
done
set -e

pushd swift-t > /dev/null
# update submodules to latest github revs
for subrepo in $subrepos
do
  echo "Updating submodule $subrepo"
  pushd $subrepo > /dev/null
  git pull --rebase origin master
  git submodule init
  git submodule update
  git submodule status
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

