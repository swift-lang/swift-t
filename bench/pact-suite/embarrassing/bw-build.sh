#!/bin/bash

set -e

INST=$HOME/soft/exm-sc14/v1/
TURBINE=$INST/turbine
LB=$INST/lb
CUTILS=$INST/c-utils
STC_INST=$INST/stc

source $TURBINE/scripts/turbine-build-config.sh

CC=cc
CFLAGS="-std=c99 -O2 ${TURBINE_INCLUDES}"
LDFLAGS="${TURBINE_LIBS} ${TURBINE_RPATH}"

MKSTATIC=$TURBINE/scripts/mkstatic/mkstatic.tcl

${CC} ${CFLAGS} embarrassing.c  ${LDFLAGS} -o embarrassing 

STC=$STC_INST/bin/stc
STC_OPTLEVEL=${STC_OPTLEVEL:--O2}
STC_FLAGS="$STC_OPTLEVEL"
STC_FLAGS+=" -T no-engine"

${STC} ${STC_FLAGS} -C embarrassing_lognorm.ic embarrassing_lognorm.swift
${MKSTATIC} embarrassing_lognorm.manifest -c embarrassing_lognorm_tcl.c

# Statically link
${CC} ${CFLAGS} embarrassing_lognorm_tcl.c  ${LDFLAGS} -o embarrassing_lognorm_tcl

echo "OK."
