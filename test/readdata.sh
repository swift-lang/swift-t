#!/bin/bash

set -x

THIS=$0
SCRIPT=${THIS%.sh}.tcl
OUTPUT=${THIS%.sh}.out

INPUT=test/data/input.txt
echo v{0..2} | xargs -n 1 > ${INPUT} || exit 1

mpiexec -l -n 3 bin/turbine ${SCRIPT} >& ${OUTPUT}
[[ ${?} == 0 ]] || exit 1

LINES=$( grep -c "trace: v[0-2]" ${OUTPUT} )
[[ ${LINES} == 3 ]] || exit 1

rm -v ${INPUT} || exit 1

exit 0
