#!/bin/bash

TESTS=$( dirname $0 )

set -x

THIS=$0
BIN=${THIS%.sh}.x
OUTPUT=${THIS%.sh}.out

mpiexec -n 2 ${BIN} >& ${OUTPUT}
[[ ${?} == 0 ]] || exit 1

SIZE=$( grep -c "size: 16" ${OUTPUT} )
(( ${SIZE} == 2 )) || exit 1

R=$( grep -c "r: 8" ${OUTPUT} )
(( ${R} == 2 )) || exit 1

exit 0
