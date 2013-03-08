#!/bin/bash

# Set up and run MPI test case

BIN=$1
PROCS=3

pwd
which mpiexec

set -x
source scripts/turbine-config.sh

mpiexec -l -n ${PROCS} ${VALGRIND} ${BIN}
