#!/bin/bash

set -x

THIS=$0
SCRIPT=${THIS%.sh}.tcl
OUTPUT=${THIS%.sh}.out

turbine -n 4 ${SCRIPT} >& ${OUTPUT}
[[ ${?} == 0 ]] || exit 1

LINES=$( ls test/data/{1..9}.txt | wc -l )
(( ${LINES} == 9 )) || exit 1

rm test/data/{1..9}.txt || exit 1

exit 0
