#!/bin/bash -ex

MPICC=$( which mpicc )
MPI=$( dirname $( dirname ${MPICC} ) )

TURBINE=$( which turbine )
TURBINE_HOME=$( dirname $( dirname ${TURBINE} ) )
TURBINE_INCLUDE=${TURBINE_HOME}/include
TURBINE_LIB=${TURBINE_HOME}/lib

# Obtain Turbine build configuration
source ${TURBINE_HOME}/scripts/turbine-config.sh

C_UTILS_INCLUDE=${C_UTILS}/include
ADLB_INCLUDE=${ADLB}/include

INCLUDES="-I . -I ${TURBINE_INCLUDE} ${TCL_INCLUDE_SPEC} "
INCLUDES+="-I ${C_UTILS_INCLUDE} -I ${ADLB_INCLUDE}"

stc -r ${PWD} -r ${TURBINE_LIB} test-f.swift test-f.tcl

${MPICC} -c ${INCLUDES} controller.c
${MPICC} -o controller.x controller.o \
  -L ${TURBINE_LIB} -l tclturbine \
  -Wl,-rpath -Wl,${TURBINE_LIB}
