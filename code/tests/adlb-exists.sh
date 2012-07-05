#!/bin/bash

set -x

THIS=$0
SCRIPT=${THIS%.sh}.tcl
OUTPUT=${THIS%.sh}.out

bin/turbine -l -n 2 ${SCRIPT} >& ${OUTPUT}
[[ ${?} == 0 ]] || exit 1

grep "nope: 0"   ${OUTPUT} || exit 1
grep "nope: 1"   ${OUTPUT} || exit 1
grep "nope: 2"   ${OUTPUT} || exit 1
grep "nope: 3"   ${OUTPUT} || exit 1
grep "exists: 4" ${OUTPUT} || exit 1

exit 0
