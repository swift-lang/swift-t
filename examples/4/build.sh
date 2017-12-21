#!/bin/bash -eu

TURBINE=$( which turbine )
source $( $TURBINE -C )
source ${TURBINE_HOME}/scripts/turbine-config.sh

# Wrap the Fortran in C++
fortwrap.py --array-as-ptr --no-vector --no-fmat mvm.f
# Wrap the C++ in Tcl
swig -c++ -module mvm FortFuncs.h
# Minor fix to the wrapper code
sed -i '11i#include "FortFuncs.h"' FortFuncs_wrap.cxx

# Compile everything
g++      -c -fPIC -I . FortFuncs.cpp
g++      -c -fPIC ${TCL_INCLUDE_SPEC} FortFuncs_wrap.cxx
gfortran -c -fPIC mvm.f

# Build the shared object
g++ -shared -o libmvm.so FortFuncs_wrap.o FortFuncs.o mvm.o ${BLAS} -lgfortran

# Make the Tcl package
${TCLSH} make-package.tcl > pkgIndex.tcl
