#!/usr/bin/env bash

set -e

SCRIPT_DIR=$(dirname $0)
source "${SCRIPT_DIR}/repos.sh"

ERRORS=0

for subrepo in $subrepos
do
  echo Rebasing $subrepo        
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
    echo "Removed old __tmp_master temporary branch"
  fi
  git checkout -b __tmp_master ${git_svn_remote}
  #git_svn_head_hash=$(git rev-parse $git_svn_remote)
  last_merged_hash=$(git svn find-rev $last_svn_rev $git_svn_remote)
  echo "need to merge $last_merged_hash..$git_svn_remote from git-svn"

  if [ -z "$last_merged_hash" ]; then
    echo "ERROR: Could not find $last_svn_rev on remote $git_svn_remote"
  elif ! git rebase ${last_merged_hash} --onto ${github_remote}
  then
    echo "ERROR: rebase unsuccessful"
    ERRORS=1
  fi
  
  popd > /dev/null
  echo DONE
done
