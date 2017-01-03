#!/bin/sh
set -ex

# Compile the SWIG-generated Tcl extension library

gcc -c -fPIC -Wall g.c
gcc -c -fPIC $TCL_INCLUDE_SPEC g_wrap.c
gcc -shared -o libg.so g_wrap.o g.o
tclsh make-package.tcl > pkgIndex.tcl
