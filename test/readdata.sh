#!/bin/bash

set -x

THIS=$0
SCRIPT=${THIS%.sh}.tcl
OUTPUT=${THIS%.sh}.out

bin/turbine ${SCRIPT} >& ${OUTPUT}
[[ ${?} == 0 ]] || exit 1

LINES=$( grep -c "v[0-2]" ${OUTPUT} )
[[ ${LINES} == 3 ]] || exit 1

exit 0
