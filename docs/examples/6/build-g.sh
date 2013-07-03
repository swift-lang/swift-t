#!/bin/sh -ex

MPICC=$( which mpicc )
MPI=$( dirname $( dirname ${MPICC} ) )

TCL_INCLUDE=${HOME}/sfw/tcl-8.6.0/include

swig -I${MPI}/include g.i

${MPICC} -c -fPIC -I . g.c
${MPICC} -c -fPIC -I ${TCL_INCLUDE} g_wrap.c
${MPICC} -shared -o libg.so g_wrap.o g.o
tclsh make-package-g.tcl > pkgIndex.tcl
# sed -i 's/list load/list load -global/' pkgIndex.tcl

# stc -r ${PWD} test-mpi-f.swift test-mpi-f.tcl

