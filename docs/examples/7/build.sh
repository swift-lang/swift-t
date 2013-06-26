#!/bin/sh

set -x
swig -module f mpi-f.h

mpicc -c -fPIC -I . mpi-f.c &&
mpicc -c -fPIC -I /usr/include/tcl8.5 mpi-f_wrap.c &&
mpicc -shared -o libmpi-f.so mpi-f_wrap.o mpi-f.o &&
tclsh make-package.tcl > pkgIndex.tcl
