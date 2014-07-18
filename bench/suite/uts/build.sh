#!/usr/bin/env bash
set -e

INST=$HOME/ExM/inst
TURBINE=$INST/turbine
LB=$INST/lb
CUTILS=$INST/c-utils
STC=$INST/stc

source $TURBINE/scripts/turbine-build-config.sh

pushd uts-src
export UTS_PIC=1
export TCL_HOME # Pass to Makefile
make clean

# For this build script, we're going to build a shared library
make
make libuts.so
popd

cp uts-src/libuts.so lib/libuts.so


CC=mpicc
CFLAGS="-std=c99 -Wall -O2 ${TURBINE_INCLUDES} -I. -I../util"
LDFLAGS=""
LIBS="-L uts-src -luts ${TURBINE_LIBS} ${TURBINE_RPATH}"
LIBS+=" -L ${TCL_HOME}/lib -ltcl8.6"

MKSTATIC=$TURBINE/scripts/mkstatic/mkstatic.tcl

UTS_RNG=BRG_RNG

STC=$INST/stc/bin/stc

echo -n ADLB
${CC} ${CFLAGS} ${LDFLAGS} -D ${UTS_RNG} uts_adlb.c ${LIBS} -o uts_adlb
echo .

for OPT in 0 1 2 3
do
  STC_OPTLEVEL="-O$OPT"
  STC_FLAGS="$STC_OPTLEVEL"
  STC_FLAGS+=" -T no-engine"
  
  echo -n O$OPT
  PREFIX=uts
  PREFIX_OPT=${PREFIX}.O${OPT}
  PREFIX_TCL=${PREFIX}_tcl.O${OPT}
  ${STC} ${STC_FLAGS} -C ${PREFIX_OPT}.ic ${PREFIX}.swift ${PREFIX_OPT}.tcl
  echo -n .
  ${MKSTATIC} ${PREFIX}.manifest --main-script ${PREFIX_OPT}.tcl -c ${PREFIX_TCL}.c
  echo -n .

  ${CC} ${CFLAGS} ${LDFLAGS} ${PREFIX_TCL}.c \
    uts-src/libuts.a ${LIBS} -o ${PREFIX_TCL}
  echo -n

done

echo "OK."
