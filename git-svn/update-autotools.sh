#!/usr/bin/env bash

set -e

SCRIPT_DIR=$(dirname $0)
source "${SCRIPT_DIR}/repos.sh"

CHANGES=0
ERROR=0

for dir in $autotools_dirs; do
  echo "Entering $dir"
  pushd $dir > /dev/null
  git checkout -q --detach
  if git branch -D __autotools_update &> /dev/null ; then
    echo "Removed old autotools temporary branch"
  fi
  git checkout -q -b __autotools_update github/master
  if git diff-index --exit-code HEAD ; then
    ./setup.sh
    if git diff --exit-code --name-status ; then
      echo "No changes in generated scripts"
    else
      git commit -a -m "Regenerate build scripts"
      CHANGES=1
    fi
  else
    echo "Not updating scripts: uncommitted changes"
    ERROR=1
  fi
  popd > /dev/null

  echo
done

echo

if (( CHANGES )) ; then
  echo "Some generated files changed.  You can push them now."
fi

if (( ERROR )) ; then
  echo "There was at least one error encountered previously."
fi

if (( !CHANGES && !ERROR )) ; then
  echo "No changes in generated files and no errors.  No need to push anything."
fi

echo DONE
