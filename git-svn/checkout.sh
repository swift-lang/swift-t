#!/usr/bin/env bash

SCRIPT_DIR=$(dirname $0)
source "${SCRIPT_DIR}/repos.sh"

for subrepo in $subrepos
do
  LOG=$subrepo.checkout.log
  echo "Cloning $subrepo from svn, writing log to $LOG"
  if is_branch_subrepo $subrepo; then
    # include releases but not branches
    git svn clone --trunk=trunk --tags=release \
            --prefix="svn/" -A "$SCRIPT_DIR/svn-authors.txt" \
            "$EXM_SVN/$subrepo" "$subrepo" &> "$LOG" &
  else
    git svn clone "https://svn.mcs.anl.gov/repos/exm/sfw/$subrepo" \
             $subrepo &> "$LOG" &
  fi
done

LOG="$masterrepo.checkout.log"
echo "Cloning $masterrepo from github, writing log to $LOG"
git clone "${GITHUB_ROOT}/$masterrepo.git" $masterrepo \
        &> "$LOG" &

wait
if [ $? != 0 ]
then
  echo "Clone failed, see logs for details"
  exit 1
fi

echo "Clones done."

for subrepo in $subrepos
do
  pushd $subrepo > /dev/null
  # Add remote for github repository
  if git remote rm github &> /dev/null ; then
    echo "Removed previous github remote"
  fi 
  git remote add github "$GITHUB_ROOT/exm-$subrepo.git"
  echo "Fetching $sub from github"
  git fetch github
  popd > /dev/null
done

pushd $masterrepo > /dev/null
# Add remote for github repository
if git remote rm github &> /dev/null ; then
  echo "Removed previous github remote"
fi 
git remote add github "$GITHUB_ROOT/$masterrepo.git"
echo "Fetching $masterrepo from github"
git fetch github
popd > /dev/null

echo "Checkout finished."
