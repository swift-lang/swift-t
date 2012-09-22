#!/bin/bash

set -x

THIS=$0
SCRIPT=${THIS%.sh}.tcl
OUTPUT=${THIS%.sh}.out

bin/turbine -l -n 3 ${SCRIPT} >& ${OUTPUT}
[[ ${?} == 0 ]] || exit 1

grep -q "container size: 5"  ${OUTPUT} || exit 1

exit 0
