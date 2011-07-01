#!/bin/bash

set -x

THIS=$0
SCRIPT=${THIS%.sh}.tcl
OUTPUT=${THIS%.sh}.out

bin/turbine ${SCRIPT} >& ${OUTPUT}

LINES=$( grep -c "exec.* touch ..txt" ${OUTPUT} )
(( ${LINES} >= 4 )) || exit 1

exit 0
