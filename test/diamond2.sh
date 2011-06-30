#!/bin/bash

set -x

SCRIPT=$1

OUTPUT=${SCRIPT%.tcl}.out

bin/turbine ${SCRIPT} >& ${OUTPUT}

LINES=$( grep -c "exec.* touch ..txt" ${OUTPUT} )
(( ${LINES} >= 4 )) || exit 1

exit 0
