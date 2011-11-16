#!/bin/bash

set -x

THIS=$0
SCRIPT=${THIS%.sh}.tcl
OUTPUT=${THIS%.sh}.out

turbine -n 4 ${SCRIPT} >& ${OUTPUT}
[[ ${?} == 0 ]] || exit 1

exit 0
