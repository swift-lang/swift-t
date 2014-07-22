#!/bin/bash

for i in $(seq 1 3)
do
  for s in "f1( $i ) ran on A_NEW_WORK_TYPE" \
           "f2( $i ) ran on A_NEW_WORK_TYPE" \
           "f3( $i ) ran on WORK"
  do
    if ! grep -q -F "$s" $TURBINE_OUTPUT ; then
      echo "Could not find string \"$s\" in $TURBINE_OUTPUT"
      exit 1
    fi

  done
done
