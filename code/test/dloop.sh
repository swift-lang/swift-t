#!/bin/bash

THIS=$0
SCRIPT=${THIS%.sh}.tcl
OUTPUT=${THIS%.sh}.out

source $( dirname $0 )/setup.sh > ${OUTPUT} 2>&1

set -x

bin/turbine -l -n ${PROCS} ${SCRIPT} >> ${OUTPUT} 2>&1
[[ ${?} == 0 ]] || exit 1

# Should find values from 0-9
for (( i=0 ; i<10 ; i++ ))
do
  grep -q "value: ${i}" ${OUTPUT} || exit 1
done

exit 0
