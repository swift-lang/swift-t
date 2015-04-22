#!/usr/bin/env bash

set -e

SCRIPT_DIR=$(dirname $0)
source "${SCRIPT_DIR}/repos.sh"

for subrepo in $subrepos
do
  echo Updating $subrepo        
  pushd $subrepo > /dev/null
  git svn fetch --fetch-all
  
  # rebase against trunk
  echo "Updating branch master" 
  git checkout -q master
  git svn rebase
  SVN_BRANCHES=$(git branch -r | grep 'svn/' | sed 's, *svn/,,')
  for b in ${SVN_BRANCHES}
  do
    if [[ "$b" =~ tags/.* ]]; then
      tag=`echo "$b" | sed 's,tags/,,'`
      echo "Updating tag $tag of $subrepo"
      git tag -f -a $tag -m "SVN tag $tag" remotes/svn/tags/$tag
      echo
    elif [[ "$b" != trunk && !("$b" =~ .*@.*) ]]; then
      echo "Updating branch $b of $subrepo"
      # check if branch exists
      if git checkout $b &> /dev/null; then
        # rebase against remote
        
        if git rebase remotes/svn/$b; then
          :
        else
          echo "Rebase of branch $b of $subrepo failed, you will need to resolve yourself"
          exit 1
        fi
      else
        # Checkout as local branch
        git branch $b remotes/svn/$b
      fi
      echo
    fi
  done
  echo
  git checkout -q master
  
  echo "Fetching github version"
  git fetch github

  popd > /dev/null
done

echo "Updating swift-t"
pushd swift-t > /dev/null
  git checkout master
  git pull --rebase origin master
popd > /dev/null

