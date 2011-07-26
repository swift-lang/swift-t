#!/bin/bash

set -x

THIS=$0
source $( dirname $0 )/setup.sh

mpiexec -l -n ${PROCS} bin/turbine ${SCRIPT} >& ${OUTPUT}
[[ ${?} == 0 ]] || exit 1

exit 0
