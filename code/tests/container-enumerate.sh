#!/bin/bash

THIS=$0
SCRIPT=${THIS%.sh}.tcl
OUTPUT=${THIS%.sh}.out

source $( dirname $0 )/setup.sh > ${OUTPUT} 2>&1

set -x

bin/turbine -l -n ${PROCS} ${SCRIPT} >> ${OUTPUT} 2>&1
[[ ${?} == 0 ]] || exit 1

# Read member TDs to search for them later
M1=$( sed -n '/.*member1:.*/{s/.*member1: \(.*\)/\1/;p}' ${OUTPUT} )
M2=$( sed -n '/.*member2:.*/{s/.*member2: \(.*\)/\1/;p}' ${OUTPUT} )

grep -q "subscripts: 0 1"       ${OUTPUT} || exit 1
grep -q "members: ${M1} ${M2}"  ${OUTPUT} || exit 1
grep -q "dict: 0 ${M1} 1 ${M2}" ${OUTPUT} || exit 1
grep -q "count: 2"              ${OUTPUT} || exit 1

exit 0
