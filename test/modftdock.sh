#!/bin/bash

set -x

THIS=$0
SCRIPT=${THIS%.sh}.tcl
OUTPUT=${THIS%.sh}.out

INPUT=test/data/input
ROOTS_TXT=${INPUT}/roots.txt
ROOTS=$( echo root{0..2} )

mkdir -p ${INPUT} || exit 1
echo ${ROOTS} | xargs -n 1 > ${ROOTS_TXT} || exit 1

for R in ${ROOTS}
do
  touch ${INPUT}/${R}.pdb
done

bin/turbine ${SCRIPT} --list=${ROOTS_TXT} --in=input >& ${OUTPUT}
[[ ${?} == 0 ]] || exit 1

# LINES=$( grep -c "v[0-2]" ${OUTPUT} )
# [[ ${LINES} == 3 ]] || exit 1

rm -rv ${INPUT} || exit 1

exit 0
