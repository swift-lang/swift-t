#!/bin/bash

set -x

THIS=$0
SCRIPT=${THIS%.sh}.tcl
OUTPUT=${THIS%.sh}.out

bin/turbine -n 2 ${SCRIPT} >& ${OUTPUT}
# This script is expected to fail on adlb::retrieve failure
[[ ${?} == 1 ]] || exit 1

grep "retrieve.*failed" ${OUTPUT} || exit 1

exit 0
