#!/bin/bash

TESTS=$( dirname $0 )

set -x

THIS=$0
BIN=${THIS%.sh}.x
OUTPUT=${THIS%.sh}.out
INPUT=${THIS%.sh}.txt

${TESTS}/runbin.zsh ${BIN}  >& ${OUTPUT}
[[ ${?} == 0 ]] || exit 1

LINES=$( ls test/data/[ABCD].txt | wc -l)
[[ ${LINES} == 4 ]] || exit 1

rm -v test/data/[ABCD].txt

exit 0
