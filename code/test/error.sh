#!/bin/bash

set -x

THIS=$0
SCRIPT=${THIS%.sh}.tcl
OUTPUT=${THIS%.sh}.out

bin/turbine -l -n 1 ${SCRIPT} >& ${OUTPUT}
EXITCODE=${?}
[[ ${EXITCODE} != 0 ]] || exit 1

exit 0
