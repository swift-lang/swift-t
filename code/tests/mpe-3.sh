#!/bin/bash

set -x

THIS=$0
SCRIPT=${THIS%.sh}.tcl
OUTPUT=${THIS%.sh}.out

bin/turbine -l -n 4 ${SCRIPT} >& ${OUTPUT}
[[ ${?} == 0 ]] || exit 1

OKS=$( grep -c OK ${OUTPUT} )
(( OKS == 4 )) || exit 1

exit 0
