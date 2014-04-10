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

${CC} ${CFLAGS} embarrassing.c  ${LDFLAGS} -o embarrassing 
${CC} ${CFLAGS} -D LOGNORM embarrassing.c  ${LDFLAGS} -o embarrassing_lognorm

STC=$STC_INST/bin/stc

for OPT in 0 1 2 3
do
  STC_OPTLEVEL="-O$OPT"
  STC_FLAGS="$STC_OPTLEVEL"
  STC_FLAGS+=" -T no-engine"

  PREFIX=embarrassing_lognorm
  PREFIX_OPT=${PREFIX}.O${OPT}
  PREFIX_TCL=${PREFIX}_tcl.O${OPT}
  ${STC} ${STC_FLAGS} -C ${PREFIX_OPT}.ic ${PREFIX}.swift ${PREFIX_OPT}.tcl
  ${MKSTATIC} ${PREFIX}.manifest -c ${PREFIX_TCL}.c

  # Dynamically link
  ${CC} -dynamic ${CFLAGS} ${PREFIX_TCL}.c ${LDFLAGS} -o ${PREFIX_TCL}

done

echo "OK."
