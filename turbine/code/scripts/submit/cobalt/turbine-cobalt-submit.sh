#!/bin/bash

export TCLLIBPATH=/f/g
export X=2

MPI=${HOME}/sfw/mpich-3.0.3-x86_64-mx
${MPI}/bin/mpiexec -l -machinefile ${COBALT_NODEFILE} -np 10 $PWD/test.sh



