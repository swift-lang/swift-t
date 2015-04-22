#!/bin/bash -eu

# Note Fortran memory layout:
turbine-write-doubles A.data 1 3 2 4
turbine-write-doubles x.data 5 6

gfortran -c mvm.f
gfortran -c test-mvm.f
gfortran -o test-mvm.x test-mvm.o mvm.o ${BLAS}

./test-mvm.x
