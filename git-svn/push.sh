#!/usr/bin/env bash

set -e

SCRIPT_DIR=$(dirname $0)
source "${SCRIPT_DIR}/repos.sh"

ERRORS=0

for subrepo in $subrepos
do
  echo Pushing $subrepo        
  pushd $subrepo > /dev/null
  
  if is_branch_subrepo $subrepo ; then
    git_svn_remote="remotes/svn/trunk"
  else
    git_svn_remote="remotes/git-svn"
  fi

  github_remote="remotes/github/master"
  last_svn_rev=$(git log ${github_remote} | grep git-svn-id | head -n1 | grep -o '@[0-9]*' | sed 's/@/r/')
  echo "Last svn revision on github: $last_svn_rev"

  git checkout -q --detach
  if git branch -D __tmp_master &> /dev/null ; then
    echo "Deleted old __tmp_master"
  fi
  git fetch github
  git checkout -b __tmp_master ${git_svn_remote}
  #git_svn_head_hash=$(git rev-parse $git_svn_remote)
  last_merged_hash=$(git svn find-rev $last_svn_rev $git_svn_remote)
  echo "need to merge $last_merged_hash..$git_svn_remote from git-svn"
  
  
  if ! git rebase ${last_merged_hash} --onto ${github_remote}
  then
    echo "ERROR: rebase unsuccessful"
    ERRORS=1
  elif ! git push --tags github HEAD:master
  then
    echo "ERROR: Not all refs were pushed ok, check previous output"
    ERRORS=1
  fi
  
  git checkout -q --detach
  git branch -D __tmp_master &> /dev/null
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
