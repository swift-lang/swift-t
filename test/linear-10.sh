#!/bin/bash

set -x

THIS=$0
SCRIPT=${THIS%.sh}.tcl
OUTPUT=${THIS%.sh}.out

mpiexec -l -n 4 bin/turbine ${SCRIPT} >& ${OUTPUT}
[[ ${?} == 0 ]] || exit 1

LINES=$( ls test/data/[1-9].txt | wc -l )
(( ${LINES} == 9 )) || exit 1

rm -v test/data/{1..9}.txt || exit 1

exit 0
