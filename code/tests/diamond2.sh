#!/bin/bash

# set -x

THIS=$0
SCRIPT=${THIS%.sh}.tcl
OUTPUT=${THIS%.sh}.out

source $( dirname $0 )/setup.sh > ${OUTPUT} 2>&1

bin/turbine -l -n ${PROCS} ${SCRIPT} >> ${OUTPUT} 2>&1
[[ ${?} == 0 ]] || exit 1

LINES=$( ls tests/data/[ABCD].txt | wc -l )
(( ${LINES} == 4 )) || exit 1

rm -v tests/data/[ABCD].txt || exit 1

exit 0
