#!/bin/sh

# CP-PARENTS
# Substitute for cp --parents on non-GNU systems

usage()
{
  echo "usage: cp-parents.sh ARGS SRC/FILE DEST"
  echo "Copies SRC/FILE to DEST/SRC/FILE"
  echo "ARGS must start with dash and are passed to cp"
  echo "Emulates GNU's cp --parents"
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
  if [ ${?} != 0 ]
  then
    crash "cp-parents.sh: failed on command:\n  ${COMMAND}"
  fi
}

ARGS=
if [ $1 = "-*" ]
then
  ARGS=$1
  shift
fi

if [ ${#*} -lt 2 ]
then
  crash "Requires SRC, DEST"
fi

SRCS=
while [ ${#*} -gt 1 ]
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
