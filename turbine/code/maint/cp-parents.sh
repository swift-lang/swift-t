#!/bin/bash

# CP-PARENTS
# Substitute for cp --parents on non-GNU systems
# We use bash because dash is not on the Mac by default

usage()
{
  echo "usage: cp-parents.sh [ARGS] SRC/FILE* DEST"
  echo "Copies SRC/FILE* to DEST/SRC/FILE"
  echo "A single token ARGS is optional, and must start with dash"
  echo "Emulates GNU's cp $ARGS --parents SRC/FILE* DEST"
}

crash()
{
  MSG=$1
  echo $MSG
  echo
  usage
  exit 1
}

try()
{
  COMMAND=${*}
  $COMMAND
  if (( ${?} ))
  then
    crash "cp-parents.sh: failed on command:\n  ${COMMAND}"
  fi
}

ARGS=""
# This pattern requires bash (not dash):
if [[ $1 = -* ]]
then
  ARGS=$1
  shift
fi

if (( ${#*} < 2 ))
then
  crash "Requires SRC, DEST"
fi

SRCS=
while (( ${#*} > 1 ))
do
  SRCS="$SRCS $1"
  shift
done

DEST=$1

for SRC in $SRCS
do
  TARGET=$DEST/$SRC
  DIR=$( dirname $TARGET )
  try mkdir -p $DIR
  try cp $ARGS $SRC $TARGET
done

exit 0
