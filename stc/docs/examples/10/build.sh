#!/bin/bash -e

MPICC=$( which mpicc )
MPI=$( dirname $( dirname $MPICC ) )

# Obtain Turbine build variables
source $(turbine -S)
export TCL_INCLUDE_SPEC

make -f build.mk
