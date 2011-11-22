#!/bin/bash

THIS=$0
SCRIPT=${THIS%.sh}.tcl
OUTPUT=${THIS%.sh}.out

source $( dirname $0 )/setup.sh > ${OUTPUT} 2>&1

set -x

INPUT=test/data/input.txt
echo v{0..2} | xargs -n 1 > ${INPUT} || exit 1

bin/turbine -l -n ${PROCS} ${SCRIPT} >> ${OUTPUT} 2>&1
[[ ${?} == 0 ]] || exit 1

LINES=$( grep -c "trace: v[0-2]" ${OUTPUT} )
[[ ${LINES} == 3 ]] || exit 1

rm -v ${INPUT} || exit 1

exit 0
