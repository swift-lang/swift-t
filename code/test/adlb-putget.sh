#!/bin/bash

set -x

THIS=$0
SCRIPT=${THIS%.sh}.tcl
OUTPUT=${THIS%.sh}.out

${LAUNCH} -n 3 bin/turbine ${SCRIPT} >& ${OUTPUT}
[[ ${?} == 0 ]] || exit 1

grep -q "answer_rank: 0" ${OUTPUT} || exit 1
grep -q "msg: hello"     ${OUTPUT} || exit 1

LINES=$( grep -c OK ${OUTPUT} )
[[ ${LINES} == 3 ]] || exit 1

exit 0
