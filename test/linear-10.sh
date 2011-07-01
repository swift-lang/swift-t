#!/bin/bash

set -x

THIS=$0
SCRIPT=${THIS%.sh}.tcl
OUTPUT=${THIS%.sh}.out

bin/turbine ${SCRIPT} >& ${OUTPUT}

LINES=$( ls [0-9].txt | wc -l )
(( ${LINES} == 10 )) || exit 1

rm {0..9}.txt || exit 1

exit 0
