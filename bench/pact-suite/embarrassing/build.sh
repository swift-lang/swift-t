#!/bin/bash

set -e

INST=$HOME/ExM/inst
TURBINE=$INST/turbine
LB=$INST/lb
CUTILS=$INST/c-utils
STC=$INST/stc

source $TURBINE/scripts/turbine-build-config.sh

CC=mpicc
CFLAGS="-std=c99 -Wall -O2 ${TURBINE_INCLUDES}"
LDFLAGS="${TURBINE_LIBS} ${TURBINE_RPATH}"

MKSTATIC=$TURBINE/scripts/mkstatic/mkstatic.tcl

${CC} ${CFLAGS} embarrassing.c  ${LDFLAGS} -o embarrassing 
${CC} ${CFLAGS} -D LOGNORM embarrassing.c  ${LDFLAGS} -o embarrassing_lognorm 

STC=$INST/stc/bin/stc
STC_OPTLEVEL=${STC_OPTLEVEL:--O2}
STC_FLAGS="$STC_OPTLEVEL"
STC_FLAGS+=" -T no-engine"

${STC} ${STC_FLAGS} -C embarrassing_lognorm.ic embarrassing_lognorm.swift
${MKSTATIC} embarrassing_lognorm.manifest -c embarrassing_lognorm_tcl.c
${CC} ${CFLAGS} embarrassing_lognorm_tcl.c  ${LDFLAGS} -o embarrassing_lognorm_tcl

echo "OK."
