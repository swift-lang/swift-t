#!/bin/bash

set -x

THIS=$0
SCRIPT=${THIS%.sh}.tcl
OUTPUT=${THIS%.sh}.out

mpiexec -l -n 3 bin/turbine ${SCRIPT} >& ${OUTPUT}
[[ ${?} == 0 ]] || exit 1

grep -q "trace: 0,1" ${OUTPUT} || exit 1
grep -q "trace: 1,2" ${OUTPUT} || exit 1
grep -q "trace: 2,3" ${OUTPUT} || exit 1
grep -q "trace: 3,4" ${OUTPUT} || exit 1

exit 0
