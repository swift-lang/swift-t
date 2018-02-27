#!/bin/bash
set -e

# Compile the SWIG-generated Tcl extension library

# Load the Turbine build settings (TCL_INCLUDE_SPEC)
source $( turbine -C )

set -x
gcc -c -fPIC -Wall g.c
gcc -c -fPIC $TCL_INCLUDE_SPEC g_wrap.c
gcc -shared -o libg.so g_wrap.o g.o
tclsh make-package.tcl > pkgIndex.tcl
