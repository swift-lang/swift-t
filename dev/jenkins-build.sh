#!/bin/zsh
set -eu

# JENKINS BUILD SH
# Install Swift/T from Git under various techniques

setopt PUSHD_SILENT

# Get the Swift/T source directory, canonicalized
SWIFT_T=${ZSH_ARGZERO:A:h:h}

cd $SWIFT_T

source dev/helpers.sh

set -x

GIT_CHANGED=1

print "New timestamp:"
git-log | tee timestamp-new.txt

if [[ -r timestamp-old.txt ]] {
  print "Old timestamp:"
  cat timestamp-old.txt
  if diff -q timestamp-{old,new}.txt
  then
    GIT_CHANGED=0
  fi
}

echo GIT_CHANGED=$GIT_CHANGED

if (( ! GIT_CHANGED )) {
  print "Git did not change - exit."
  exit
}

rm -rv dev/build/swift-t-settings.sh
dev/build/init-settings.sh



nice -n 19 dev/build/build-swift-t.sh

# Prevent future rebuild until Git changes
cp -v timestamp-{new,old}.txt
