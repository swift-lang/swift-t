#!/bin/bash

set -x

THIS=$0
SCRIPT=${THIS%.sh}.tcl
OUTPUT=${THIS%.sh}.out

mkdir test/data

source scripts/turbine-config.sh
echo PRELOAD $LD_PRELOAD
${TURBINE_LAUNCH} -l -n 4 ${VALGRIND} ${TCLSH} ${SCRIPT} \
                  test/batcher.txt >& ${OUTPUT}
[[ ${?} == 0 ]] || exit 1

LINES=$( ls test/data/{1..4}.txt | wc -l )
(( ${LINES} == 4 )) || exit 1

rm test/data/{1..4}.txt || exit 1

exit 0
