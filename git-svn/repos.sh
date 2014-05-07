#!/usr/bin/env bash

# Subrepos with trunk/branch/tags
branch_subrepos="c-utils lb turbine stc"
# Subrepos with no branches
nonbranch_subrepos="dev"
subrepos="$branch_subrepos $nonbranch_subrepos"
masterrepo="swift-t"

allrepos="$subrepos $devrepo $masterrepo"

is_branch_subrepo() {
  local repo=$1
  for r in $branch_subrepos
  do
    if [ "$r" = "$repo" ]
    then
      return 0
    fi
  done
  return 1
}

# Directories with ./setup.sh script to run autotools
autotools_dirs="c-utils lb/code turbine/code"

