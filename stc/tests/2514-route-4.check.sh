#!/bin/sh
set -e

if grep -q "DEFAULT-SHOULD-NOT-RUN" $TURBINE_OUTFILE
then
  echo "default ran for a key another branch took!"
  exit 1
fi
