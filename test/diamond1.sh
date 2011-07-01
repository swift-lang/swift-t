#!/bin/bash

TESTS=$( dirname $0 )

set -x

THIS=$0
BIN=${THIS%.sh}.x
OUTPUT=${THIS%.sh}.out
INPUT=${THIS%.sh}.txt

${TESTS}/runbin.zsh ${BIN}  >& ${OUTPUT}
[[ ${?} == 0 ]] || exit 1

LINES=$( grep -c "cp A.txt" ${OUTPUT} )
[[ ${LINES} == 2 ]] || exit 1
grep "cat B.txt C.txt" ${OUTPUT} || exit 1

exit 0
