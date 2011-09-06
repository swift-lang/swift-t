#!/bin/bash

THIS=$0
SCRIPT=${THIS%.sh}.tcl
OUTPUT=${THIS%.sh}.out

source $( dirname $0 )/setup.sh > ${OUTPUT} 2>&1

set -x

${LAUNCH} -l -n ${PROCS} bin/turbine ${SCRIPT} >> ${OUTPUT} 2>&1
[[ ${?} == 0 ]] || exit 1

LINES=$( ls test/data/[1-9].txt | wc -l )
(( ${LINES} == 9 )) || exit 1

rm -v test/data/{1..9}.txt || exit 1

exit 0
