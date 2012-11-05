#!/bin/bash

TESTS=$( dirname $0 )

set -x

THIS=$0
BIN=${THIS%.sh}.x
OUTPUT=${THIS%.sh}.out

${VALGRIND} ${BIN} >& ${OUTPUT}
[[ ${?} == 0 ]] || exit 1

grep DONE ${OUTPUT} || exit 1

exit 0
