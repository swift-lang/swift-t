#!/bin/bash

TESTS=$( dirname $0 )

set -x

BIN=$1
OUTPUT=${BIN%.x}.out

${TESTS}/runbin.zsh ${BIN} >& ${OUTPUT}
[[ ${?} == 0 ]] || exit 1

grep DONE ${OUTPUT} || exit 1

exit 0
