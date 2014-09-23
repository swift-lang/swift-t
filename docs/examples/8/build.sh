#!/bin/sh -ex

MPICC=$( which mpicc )
MPI=$( dirname $( dirname $MPICC ) )

swig -I$MPI/include f.i

$MPICC -c -fPIC -I . f.c
$MPICC -c -fPIC $TCL_INCLUDE_SPEC f_wrap.c
$MPICC -shared -o libf.so f_wrap.o f.o
tclsh make-package.tcl > pkgIndex.tcl

stc -r $PWD test-f.swift
