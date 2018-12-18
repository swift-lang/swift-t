#!/bin/sh
set -e

N=$( grep "trace: " $TURBINE_OUTPUT | wc -w )
if [ $N != 4 ]
then
  echo "Should have 4 tokens on 'trace:' line!"
  exit 1
fi
