#!/bin/bash

TESTS=$( dirname $0 )

set -x

THIS=$0
BIN=${THIS%.sh}.x
OUTPUT=${THIS%.sh}.out

${BIN} >& ${OUTPUT}
[[ ${?} == 0 ]] || exit 1

grep "version: 1.2.6" ${OUTPUT} || exit 1
grep "count: 5"       ${OUTPUT} || exit 1
grep "b1: -1"         ${OUTPUT} || exit 1
grep "b2: 1"          ${OUTPUT} || exit 1
grep "b3: 1"          ${OUTPUT} || exit 1

exit 0
