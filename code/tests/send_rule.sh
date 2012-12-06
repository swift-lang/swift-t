#!/bin/bash

THIS=$0
SCRIPT=${THIS%.sh}.tcl
OUTPUT=${THIS%.sh}.out

source $( dirname $0 )/setup.sh > ${OUTPUT} 2>&1

set -x

bin/turbine -l -n ${PROCS} ${SCRIPT} >> ${OUTPUT} 2>&1
[[ ${?} == 0 ]] || exit 1

grep -q "RAN RULE ON ENGINE"    ${OUTPUT} || exit 1
grep -q "RAN RULE ON WORKER"    ${OUTPUT} || exit 1
grep -q "RAN RULE LOCAL"    ${OUTPUT} || exit 1
grep -q "RAN RULE AFTER X"    ${OUTPUT} || exit 1

exit 0
