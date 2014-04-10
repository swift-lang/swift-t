#!/bin/bash

set -e

INST=$HOME/soft/exm-sc14/v1/
TURBINE=$INST/turbine
LB=$INST/lb
CUTILS=$INST/c-utils
STC_INST=$INST/stc

source $TURBINE/scripts/turbine-build-config.sh

CC=cc
CFLAGS="-std=c99 -Wall -O2 ${TURBINE_INCLUDES}"
LDFLAGS="${TURBINE_LIBS}"

MKSTATIC=$TURBINE/scripts/mkstatic/mkstatic.tcl

ADLB_PROG=wavefront
${CC} ${CFLAGS} ${ADLB_PROG}.c  ${LDFLAGS} -o ${ADLB_PROG} 

STC=$STC_INST/bin/stc
STC_OPTLEVEL=${STC_OPTLEVEL:--O2}
STC_FLAGS="$STC_OPTLEVEL"
STC_FLAGS+=" -T no-engine"

SWIFT_PROG=wavefront
${STC} ${STC_FLAGS} -C ${SWIFT_PROG}.ic ${SWIFT_PROG}.swift
${MKSTATIC} ${SWIFT_PROG}.manifest -c ${SWIFT_PROG}_tcl.c
${CC} ${CFLAGS} ${SWIFT_PROG}_tcl.c  ${LDFLAGS} -o ${SWIFT_PROG}_lognorm_tcl

echo "OK."
