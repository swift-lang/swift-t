#!/usr/bin/env bash
set -e
set -x

THISDIR=`dirname $0`
source ${THISDIR}/build-vars.sh

set -e
if [ -f Makefile ]; then
    make clean
fi
EXTRA_ARGS=
if [ ! -z "$EXM_OPT_BUILD" ]; then
    EXTRA_ARGS+="--enable-fast"
fi

if [ -z "$SKIP_AUTOTOOLS" ]; then
  rm -rf ./config.status ./autom4te.cache
  ./setup.sh
fi

if [ ! -z "$EXM_DEBUG_BUILD" ]; then
   export CFLAGS="-g -O0" 
fi

if [ ! -z "$EXM_CRAY" ] ; then
    export CC=gcc
    export CFLAGS="-g -O2"
fi

./configure --enable-shared --prefix=${C_UTILS_INST} ${EXTRA_ARGS}
make -j ${MAKE_PARALLELISM}
make install
