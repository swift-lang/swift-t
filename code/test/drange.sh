#!/bin/bash

THIS=$0
SCRIPT=${THIS%.sh}.tcl
OUTPUT=${THIS%.sh}.out

source $( dirname $0 )/setup.sh > ${OUTPUT} 2>&1

set -x

bin/turbine -l -n ${PROCS} ${SCRIPT} >> ${OUTPUT} 2>&1
[[ ${?} == 0 ]] || exit 1

# grep -q "trace: 0,1" ${OUTPUT} || exit 1
# grep -q "trace: 1,2" ${OUTPUT} || exit 1
# grep -q "trace: 2,3" ${OUTPUT} || exit 1
# grep -q "trace: 3,4" ${OUTPUT} || exit 1

exit 0
