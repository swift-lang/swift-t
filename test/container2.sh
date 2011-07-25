#!/bin/bash

set -x

THIS=$0
SCRIPT=${THIS%.sh}.tcl
OUTPUT=${THIS%.sh}.out

mpiexec -l -n 3 bin/turbine ${SCRIPT} >& ${OUTPUT}
[[ ${?} == 0 ]] || exit 1

grep -q "enumeration: 0 1"    ${OUTPUT} || exit 1
grep -q "filename: file1.txt" ${OUTPUT} || exit 1
grep -q "filename: file2.txt" ${OUTPUT} || exit 1

exit 0
