#!/bin/sh

set -x

grep -q 1234  ${TURBINE_OUTFILE} || exit 1
grep -q 4321  ${TURBINE_OUTFILE} || exit 1
grep -q AA    ${TURBINE_OUTFILE} || exit 1
grep -q "x=1" ${TURBINE_OUTFILE} || exit 1

echo OK

exit 0
