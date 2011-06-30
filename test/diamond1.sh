#!/bin/bash

TESTS=$( dirname $0 )

set -x

BIN=$1
OUTPUT=${BIN%.x}.out
INPUT=${BIN%.x}.txt

${TESTS}/runbin.zsh ${BIN}  >& ${OUTPUT}
[[ ${?} == 0 ]] || exit 1

LINES=$( grep -c "cp A.txt" ${OUTPUT} )
[[ ${LINES} == 2 ]] || exit 1
grep "cat B.txt C.txt" ${OUTPUT} || exit 1

exit 0
