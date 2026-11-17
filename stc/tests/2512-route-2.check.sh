#!/bin/sh
set -e

if grep -q "ABSENT-SHOULD-NOT-RUN" $TURBINE_OUTFILE
then
  echo "A case branch ran for a key that was never inserted!"
  exit 1
fi
