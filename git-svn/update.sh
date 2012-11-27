#!/usr/bin/env bash

set -e

for subrepo in c-utils lb turbine stc
do
  echo Updating $subrepo        
  pushd $subrepo > /dev/null
  git svn fetch --fetch-all
  
  # rebase against trunk
  echo "Updating branch master" 
  git checkout master
  git svn rebase
  SVN_BRANCHES=$(git branch -r | grep 'svn/' | sed 's, *svn/,,')
  for b in ${SVN_BRANCHES}
  do
    if [[ "$b" =~ tags/.* ]]; then
      tag=`echo "$b" | sed 's,tags/,,'`
      echo "Updating tag $tag"
      git tag -f -a $tag -m "SVN tag $tag" remotes/svn/tags/$tag
    elif [[ "$b" != trunk && !("$b" =~ .*@.*) ]]; then
      echo "Updating branch $b"
      # check if branch exists
      if git checkout $b &> /dev/null; then
        # rebase against remote
        git svn rebase
      else
        # Checkout as local branch
        git checkout -b $b remotes/svn/$b
      fi
    fi
  done
  git checkout master
  popd > /dev/null
done

echo "Updating dev"
pushd dev > /dev/null
  git checkout master
  git svn rebase
popd > /dev/null
