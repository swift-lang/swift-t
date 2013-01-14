#!/bin/bash

set -x

THIS=$0
SCRIPT=${THIS%.sh}.tcl
OUTPUT=${THIS%.sh}.out

export ADLB_EXHAUST_TIME=1

bin/turbine -l -n 4 ${SCRIPT} >& ${OUTPUT}
[[ ${?} == 0 ]] || exit 1

grep -q "msg: hello"     ${OUTPUT} || exit 1

LINES=$( grep -c OK ${OUTPUT} )
[[ ${LINES} == 4 ]] || exit 1

exit 0
