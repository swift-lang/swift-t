#!/bin/bash

source scripts/turbine-config.sh

set -x

THIS=$0
SCRIPT=${THIS%.sh}.tcl
OUTPUT=${THIS%.sh}.out

mkdir -p test/data || exit 1

${MPIEXEC} -l -n 4 bin/turbine ${SCRIPT} >& ${OUTPUT}
[[ ${?} == 0 ]] || exit 1

LINES=$( ls test/data/{A..D}.txt | wc -l )
(( ${LINES} == 4 )) || exit 1

rm test/data/{A..D}.txt || exit 1

exit 0
