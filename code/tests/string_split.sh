#!/bin/bash

THIS=$0
SCRIPT=${THIS%.sh}.tcl
OUTPUT=${THIS%.sh}.out

source $( dirname $0 )/setup.sh > ${OUTPUT} 2>&1

set -x

bin/turbine -l -n ${PROCS} ${SCRIPT} >> ${OUTPUT} 2>&1
[[ ${?} == 0 ]] || exit 1

grep -q  "store:.*evil name"           ${OUTPUT} || exit 1
grep -q  "store:.*/usr/bin"            ${OUTPUT} || exit 1
# Ensure these do not end up together:
grep -vq "store:.*evil name.*/usr/bin" ${OUTPUT} || exit 1

exit 0
