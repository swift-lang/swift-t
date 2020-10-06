#!/bin/zsh
set -eu

# LOCK SH
# Lock a Swift/T installation directory to prevent modification
# during runs.

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

cd $THIS

LOCKS=( lock {c-utils,lb,turbine,stc}/lock )

lock()
{
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
  local RM_V=""
  if (( VERBOSE )) {
       RM_V="-v"
  }
  if [[ ! -f lock ]] {
       print "Not locked: $THIS"
       return
  }
  rm $RM_V lock {c-utils,lb,turbine,stc}/lock
  print "Unlocked: $THIS"
}

if (( CHECK )) {
     if [[ -f $THIS/lock ]] {
          print "Installation directory is locked!  $THIS"
          return 1
     }
} else {
     if (( UNLOCK )) {
          unlock
     } else {
          lock
     }
}
