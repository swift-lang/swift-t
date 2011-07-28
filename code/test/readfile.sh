#!/bin/bash

TESTS=$( dirname $0 )

set -x

THIS=$0
BIN=${THIS%.sh}.x
OUTPUT=${THIS%.sh}.out
INPUT=${THIS%.sh}.txt

${TESTS}/runbin.zsh ${BIN} < ${INPUT} >& ${OUTPUT}
[[ ${?} == 0 ]] || exit 1

LINES=$( wc -l < ${OUTPUT} )
[[ ${LINES} == 6 ]] || exit 1
LINES=$( grep -c line ${OUTPUT} )
[[ ${LINES} == 5 ]] || exit 1
grep "\[end\]" ${OUTPUT} || exit 1

exit 0
