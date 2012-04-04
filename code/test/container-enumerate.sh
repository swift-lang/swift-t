#!/bin/bash

THIS=$0
SCRIPT=${THIS%.sh}.tcl
OUTPUT=${THIS%.sh}.out

source $( dirname $0 )/setup.sh > ${OUTPUT} 2>&1

set -x

bin/turbine -l -n ${PROCS} ${SCRIPT} >> ${OUTPUT} 2>&1
[[ ${?} == 0 ]] || exit 1

grep -q "subscripts: 0 1" ${OUTPUT} || exit 1
grep -q "members: 3 4"    ${OUTPUT} || exit 1
grep -q "dict: 0 3 1 4"   ${OUTPUT} || exit 1

exit 0
