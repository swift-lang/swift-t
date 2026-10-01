#!/bin/sh
set -e

if ! grep -q 1969 $TURBINE_OUTFILE
then
  echo "test 7451 failed: contents:"
  echo "TURBINE_OUTFILE: $TURBINE_OUTFILE"
  $TURBINE_OUTFILE
  echo "test 7451 failed: contents done."
fi
