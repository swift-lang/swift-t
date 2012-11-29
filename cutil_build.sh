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
./setup.sh
./configure --enable-shared --prefix=${C_UTILS_INST} ${EXTRA_ARGS}
make -j ${MAKE_PARALLELISM}
make install
