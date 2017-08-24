#!/bin/sh
set -e

grep -q "Custom work" ${TURBINE_OUTPUT}
if grep -q "while executing" ${TURBINE_OUTPUT}
then
  echo "Output should not contain Tcl stack trace."
  exit 1
fi
