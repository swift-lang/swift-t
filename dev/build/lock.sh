#!/usr/bin/env zsh
set -eu

# LOCK SH
# Lock a Swift/T installation directory to prevent modification
# during runs.
# Applications can lock the directory by simply touching the
# lock file in the installation directory.
# Note that this script is not installed by Spack
#      or when not using the build-swift-t scripts

UNLOCK=0
VERBOSE=0

help()
{
  print "default:" \
        "Lock the installation directory."
  print "-u:" \
        "Unlock the installation directory."
  print "-c:" \
        "Assert the installation directory is not locked, else fail."
  print "Operates on directory containing this script," \
        "or directory provided in first argument."
}

zparseopts -D -E c=C h=HELP u=U v=V

if (( ${#HELP} )) {
     help
     exit
}
if (( ${#C} )) {
     CHECK=1
}
if (( ${#U} )) {
     UNLOCK=1
}
if (( ${#V} )) {
     VERBOSE=1
}

if (( ${#} > 0 )) {
     THIS=$1
} else {
     THIS=$( readlink --canonicalize $( dirname $0 ) )
}

LOCKS=( lock {c-utils,lb,turbine,stc}/lock )

assert_cd()
# Assert directory exists and chdir to it
{
  if [[ ! -d $THIS ]] {
       print "Directory does not exist: $THIS"
       return 1
  }
  cd $THIS
}

lock()
{
  assert_cd
  if [[ -f lock ]] {
       print "Already locked: $THIS"
       if (( VERBOSE )) {
            ls -l $THIS/lock
       }
       return
  }
  touch $LOCKS
  if (( VERBOSE )) {
       ls $LOCKS
  }
  print "Locked: $THIS"
}

unlock()
{
  assert_cd
  local RM_V=""
  if (( VERBOSE )) {
       RM_V="-v"
  }
  if [[ ! -f lock ]] {
       print "Not locked: $THIS"
       return
  }
  # This one has to work
  rm $RM_V lock
  # These don't have to work
  # (a user app may create only the top-level lock)
  rm $RM_V {c-utils,lb,turbine,stc}/lock || true
  print "Unlocked: $THIS"
}

check()
{
  if [[ -f $THIS/lock ]] {
       print "Installation directory is locked!  $THIS"
       return 1
  } else {
       if (( VERBOSE )) {
            print "Not locked: $THIS"
       }
  }
}

if (( CHECK )) {
     check
} elif (( UNLOCK )) {
     unlock
} else {
     lock
}
