#!/bin/bash

# Note Fortran memory layout:
turbine-write-doubles A.data 1 3 2 4
turbine-write-doubles x.data 5 6

BLAS="$HOME/Downloads/BLAS/blas_LINUX.a"
gfortran -c MVM.f &&
gfortran -c test-MVM.f &&
gfortran -o test-MVM.x test-MVM.o MVM.o $BLAS || exit 1

./test-MVM.x
