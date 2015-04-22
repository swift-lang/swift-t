#!/bin/bash

# Set up and run MPI test case

BIN=$1
shift;

PROCS=${PROCS:-3}

pwd
echo "${TURBINE_LAUNCHER}"
set -x
source scripts/turbine-config.sh

FLAGS="${TURBINE_LINE_OUTPUT_FLAG}"


${TURBINE_LAUNCHER} ${FLAGS} -n ${PROCS} ${VALGRIND} ${BIN} "$@"
