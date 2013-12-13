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
if (( EXM_OPT_BUILD )); then
    EXTRA_ARGS+="--enable-fast"
fi

if (( ! SKIP_AUTOTOOLS )); then
  rm -rf ./config.status ./autom4te.cache
  ./setup.sh
fi

if (( EXM_DEBUG_BUILD )); then
   export CFLAGS="-g -O0" 
fi

if (( EXM_CRAY )); then
  if (( EXM_STATIC_BUILD )); then
    export CC=cc
  else
    export CC=gcc
  fi
  export CFLAGS="-g -O2"
fi

./configure --enable-shared --prefix=${C_UTILS_INST} ${EXTRA_ARGS}
make -j ${MAKE_PARALLELISM}
make install
