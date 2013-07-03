#!/bin/sh -ex

MPICC=$( which mpicc )
MPI=$( dirname $( dirname ${MPICC} ) )

TCL_INCLUDE=${HOME}/sfw/tcl-8.6.0/include

swig -I${MPI}/include f.i

${MPICC} -c -fPIC -I . f.c
${MPICC} -c -fPIC -I ${TCL_INCLUDE} f_wrap.c
${MPICC} -shared -o libf.so f_wrap.o f.o
tclsh make-package.tcl > pkgIndex.tcl

stc -r ${PWD} test-f.swift test-f.tcl

