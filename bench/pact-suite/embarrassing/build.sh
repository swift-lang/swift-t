#!/bin/bash

set -e

INST=$HOME/ExM/inst
TURBINE=$INST/turbine
LB=$INST/lb
CUTILS=$INST/c-utils

source $TURBINE/scripts/turbine-build-config.sh

CC=mpicc
CFLAGS="-std=c99 -O2 ${TURBINE_INCLUDES}"
LDFLAGS="${TURBINE_LIBS} ${TURBINE_RPATH}"

MKSTATIC=$TURBINE/scripts/mkstatic/mkstatic.tcl

${CC} ${CFLAGS} embarrassing.c  ${LDFLAGS} -o embarrassing 

${MKSTATIC} embarrassing_lognorm.manifest -c embarrassing_lognorm_tcl.c
${CC} ${CFLAGS} embarrassing_lognorm_tcl.c  ${LDFLAGS} -o embarrassing_lognorm_tcl

echo "OK."
