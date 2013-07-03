#!/bin/sh -ex

MPICC=$( which mpicc )
MPI=$( dirname $( dirname ${MPICC} ) )

TCL_INCLUDE=${HOME}/sfw/tcl-8.6.0/include

swig -I${MPI}/include mpi-f.i

mpicc -c -fPIC -I . mpi-f.c
mpicc -c -fPIC -I ${TCL_INCLUDE} mpi-f_wrap.c
mpicc -shared -o libmpi-f.so mpi-f_wrap.o mpi-f.o
tclsh make-package.tcl > pkgIndex.tcl
sed -i 's/list load/list load -global/' pkgIndex.tcl

stc -r ${PWD} test-mpi-f.swift test-mpi-f.tcl

