#!/bin/bash

# set -x

THIS=$0
SCRIPT=${THIS%.sh}.tcl
OUTPUT=${THIS%.sh}.out

source $( dirname $0 )/setup.sh > ${OUTPUT} 2>&1

${LAUNCH} -l -n ${PROCS} bin/turbine ${SCRIPT} >> ${OUTPUT} 2>&1
[[ ${?} == 0 ]] || exit 1

grep "a=,3" ${OUTPUT} || exit 1

exit 0
