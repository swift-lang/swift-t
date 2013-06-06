#!/bin/sh

BLAS="$HOME/Downloads/BLAS/blas_LINUX.a"

# Wrap the Fortran in C++
fortwrap.py --array-as-ptr --no-vector --no-fmat MVM.f
# Wrap the C++ in Tcl
swig -c++ -module mvm FortFuncs.h
# Minor fix to the wrapper code
sed -i '11i#include "FortFuncs.h"' FortFuncs_wrap.cxx

# Compile everything
g++      -c -fPIC -I . FortFuncs.cpp
g++      -c -fPIC -I /usr/include/tcl8.5 FortFuncs_wrap.cxx
gfortran -c -fPIC MVM.f

# Build the shared object
g++ -shared -o libmvm.so FortFuncs_wrap.o FortFuncs.o MVM.o $BLAS -lgfortran

# Make the Tcl package
tclsh make-package.tcl > pkgIndex.tcl
