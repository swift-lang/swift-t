#!/bin/bash

set -x

THIS=$0
SCRIPT=${THIS%.sh}.tcl
OUTPUT=${THIS%.sh}.out

bin/turbine -n 2 ${SCRIPT} >& ${OUTPUT}
[[ ${?} == 0 ]] || exit 1

grep "retrieve.*failed" ${OUTPUT} || exit 1

exit 0
