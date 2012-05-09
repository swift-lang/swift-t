#!/bin/bash

THIS=$0
SCRIPT=${THIS%.sh}.tcl
OUTPUT=${THIS%.sh}.out

source $( dirname $0 )/setup.sh > ${OUTPUT} 2>&1

set -x

bin/turbine -l -n ${PROCS} \
  ${SCRIPT} -F=valz file1.txt file2.txt >> ${OUTPUT} 2>&1
[[ ${?} == 0 ]] || exit 1

# grep -q "hi" ${OUTPUT} || exit 1

exit 0
