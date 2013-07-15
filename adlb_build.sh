#!/usr/bin/env bash
set -e
set -x

THISDIR=`dirname $0`
source ${THISDIR}/build-vars.sh

export CC=mpicc
if [ -f Makefile ]; then
    make clean
fi

if [ -z "$SKIP_AUTOTOOLS" ]; then
  rm -rf ./config.status ./autom4te.cache
  ./setup.sh
fi

EXTRA_ARGS=
if [ ! -z "$EXM_OPT_BUILD" ]; then
    EXTRA_ARGS+="--enable-fast "
fi

if [ ! -z "$EXM_DEBUG_BUILD" ]; then
    EXTRA_ARGS+="--enable-log-debug "
fi

if [ ! -z "$EXM_TRACE_BUILD" ]; then
    EXTRA_ARGS+="--enable-log-trace "
fi

if [ ! -z "$ENABLE_MPE" ]; then
    EXTRA_ARGS+="--with-mpe=${MPE_INST} "
fi

if [ ! -z "$EXM_CRAY" ]; then
    export CC="gcc"
    export CFLAGS="-g -O2 -I${MPICH_INST}/include"
    export LDFLAGS="-L${MPICH_INST}/lib -lmpich"
    EXTRA_ARGS+=" --enable-mpi-2"
fi

./configure --with-c-utils=${C_UTILS_INST} \
            --prefix=${LB_INST} ${EXTRA_ARGS}
make clean
make -j ${MAKE_PARALLELISM}
make install
