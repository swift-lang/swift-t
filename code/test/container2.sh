#!/bin/bash

THIS=$0
SCRIPT=${THIS%.sh}.tcl
OUTPUT=${THIS%.sh}.out

source $( dirname $0 )/setup.sh > ${OUTPUT} 2>&1

set -x

mpiexec -l -n ${PROCS} bin/turbine ${SCRIPT} >> ${OUTPUT} 2>&1
[[ ${?} == 0 ]] || exit 1

grep -q "enumeration: 0 1"    ${OUTPUT} || exit 1
grep -q "filename: file1.txt" ${OUTPUT} || exit 1
grep -q "filename: file2.txt" ${OUTPUT} || exit 1

exit 0
