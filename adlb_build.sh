#!/usr/bin/env bash
set -e
set -x

THISDIR=`dirname $0`
source ${THISDIR}/build-vars.sh

export CC=mpicc
if [ -f Makefile ]; then
    make clean
fi
./setup.sh

EXTRA_ARGS=
if [ ! -z "$EXM_OPT_BUILD" ]; then
    EXTRA_ARGS+="--enable-fast"
fi

./configure --with-mpe=${MPE_INST} --disable-f77 \
            --with-c-utils=${C_UTILS_INST} \
            --prefix=${LB_INST} ${EXTRA_ARGS}
make clean
make -j ${MAKE_PARALLELISM}
make install
