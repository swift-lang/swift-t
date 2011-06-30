#!/bin/sh

TESTS=$( dirname $0 )

set -x

BIN=$1
OUTPUT=${BIN%.x}.out
INPUT=${BIN%.x}.txt

${TESTS}/runbin.zsh ${BIN} < ${INPUT} >& ${OUTPUT}
[[ ${?} == 0 ]] || exit 1

LINES=$( wc -l < ${OUTPUT} )
[[ ${LINES} == 6 ]] || exit 1
LINES=$( grep -c line ${OUTPUT} )
[[ ${LINES} == 5 ]] || exit 1
grep "\[end\]" ${OUTPUT} || exit 1

exit 0
