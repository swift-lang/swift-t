#!/bin/bash

set -x

THIS=$0
SCRIPT=${THIS%.sh}.tcl
OUTPUT=${THIS%.sh}.out

bin/turbine ${SCRIPT} >& ${OUTPUT}
[[ ${?} == 0 ]] || exit 1

grep -q "enumeration: 0 1" ${OUTPUT} || exit 1
grep -q "file1.txt"        ${OUTPUT} || exit 1
grep -q "file2.txt"        ${OUTPUT} || exit 1

exit 0
