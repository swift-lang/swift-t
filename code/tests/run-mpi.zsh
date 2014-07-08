#!/bin/bash

# Set up and run MPI test case

BIN=$1
shift;

PROCS=${PROCS:-3}

pwd
echo "${TURBINE_LAUNCHER}"
set -x
source scripts/turbine-config.sh

${TURBINE_LAUNCHER} -l -n ${PROCS} ${VALGRIND} ${BIN} "$@"
