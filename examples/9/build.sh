#!/bin/bash
set -eu

MPICC=$( which mpicc )
MPI=$( dirname $( dirname ${MPICC} ) )

# Need to split these lines to catch errors:
CONFIG_SCRIPT=$( turbine -C )
source $CONFIG_SCRIPT

TURBINE_INCLUDE=${TURBINE_HOME}/include
TURBINE_LIB=${TURBINE_HOME}/lib

INCLUDES="-I . ${TURBINE_INCLUDES}"

# Remove this problematic library:
# Cf. https://www.linuxquestions.org/questions/slackware-14/tcl-linking-library-not-found-4175623418
TURBINE_LIBS=${TURBINE_LIBS/-lieee/}

set -x

stc -r ${PWD} -r ${TURBINE_LIB} test-f.swift test-f.tic

${MPICC} -c -Wall ${INCLUDES} controller.c
${MPICC} -o controller.x controller.o -L ${MPI_LIB_DIR} \
         ${TURBINE_LIBS} ${TURBINE_RPATH}
