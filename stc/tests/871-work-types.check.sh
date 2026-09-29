#!/bin/sh
set -e

grep -q "Custom work" ${TURBINE_OUTFILE}
if grep -q "while executing" ${TURBINE_OUTFILE}
then
  echo "Output should not contain Tcl stack trace."
  exit 1
fi
