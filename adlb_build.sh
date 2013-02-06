#!/usr/bin/env bash
set -e
set -x

THISDIR=`dirname $0`
source ${THISDIR}/build-vars.sh

export CC=mpicc
if [ -f Makefile ]; then
    make clean
fi

rm -rf ./config.status ./autom4te.cache
./setup.sh

EXTRA_ARGS=
if [ ! -z "$EXM_OPT_BUILD" ]; then
    EXTRA_ARGS+="--enable-fast"
fi

if [ ! -z "$EXM_DEBUG_BUILD" ]; then
    EXTRA_ARGS+="--enable-log-debug"
fi

if [ ! -z "$ENABLE_MPE"]; then
    EXTRA_ARGS+="--with-mpe=${MPE_INST}"
fi

./configure  --disable-f77 \
            --with-c-utils=${C_UTILS_INST} \
            --prefix=${LB_INST} ${EXTRA_ARGS}
make clean
make -j ${MAKE_PARALLELISM}
make install
