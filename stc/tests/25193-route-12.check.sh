#!/bin/sh
set -e

# 3 keys pass through 4 stages; "xy" never matches the pattern.
# Strip CRs: some launchers end lines with "\r\n".
OUT=$( tr -d '\r' < $TURBINE_OUTFILE )
N=$( printf '%s\n' "$OUT" | grep -c "trace: pattern:" )
if [ $N != 12 ]
then
  echo "Expected 12 pattern branch runs, got $N"
  exit 1
fi
if printf '%s\n' "$OUT" | grep "trace: A.3.:" | grep -q "xy"
then
  echo "xy should not reach A[3]"
  exit 1
fi
