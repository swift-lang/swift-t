#!/bin/sh

TESTS=$( dirname $0 )

BIN=$1
OUTPUT=${BIN%.x}.out

[[ ${TURBINE_DEBUG_TEST} != "" ]] && set -x

${TESTS}/runbin.zsh ${BIN} >& ${OUTPUT}
[[ ${?} == 0 ]] || exit 1

grep DONE ${OUTPUT} || exit 1

exit 0
