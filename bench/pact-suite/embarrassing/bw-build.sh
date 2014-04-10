#!/bin/bash

set -e

INST=$HOME/soft/exm-sc14/v1/
#INST=$HOME/soft/exm-sc14/v1-debug/
TURBINE=$INST/turbine
LB=$INST/lb
CUTILS=$INST/c-utils
STC_INST=$INST/stc

source $TURBINE/scripts/turbine-build-config.sh

CC=cc
CFLAGS="-std=c99 -Wall -O2 ${TURBINE_INCLUDES}"
LDFLAGS=""
LIBS="${TURBINE_LIBS}"

MKSTATIC=$TURBINE/scripts/mkstatic/mkstatic.tcl

echo -n ADLB
${CC} ${CFLAGS} ${LDFLAGS} embarrassing.c ${LIBS} -o embarrassing
echo -n .
${CC} ${CFLAGS} ${LDFLAGS} -D LOGNORM embarrassing.c ${LIBS} -o embarrassing_lognorm
echo .

STC=$STC_INST/bin/stc

for OPT in 0 1 2 3
do
  STC_OPTLEVEL="-O$OPT"
  STC_FLAGS="$STC_OPTLEVEL"
  STC_FLAGS+=" -T no-engine"
  
  echo -n O$OPT
  PREFIX=embarrassing_lognorm
  PREFIX_OPT=${PREFIX}.O${OPT}
  PREFIX_TCL=${PREFIX}_tcl.O${OPT}
  ${STC} ${STC_FLAGS} -C ${PREFIX_OPT}.ic ${PREFIX}.swift ${PREFIX_OPT}.tcl
  echo -n .
  ${MKSTATIC} ${PREFIX}.manifest --main-script ${PREFIX_OPT}.tcl -c ${PREFIX_TCL}.c
  echo -n .

  TCL_LDFLAGS="-dynamic" # Can't be linked statically
  
  ${CC} ${CFLAGS} ${TCL_LDFLAGS} ${LDFLAGS} ${PREFIX_TCL}.c \
    $(${MKSTATIC} ${PREFIX}.manifest --link-objs --link-flags) \
    ${LIBS} -o ${PREFIX_TCL}
  echo -n

done

echo "DONE"
