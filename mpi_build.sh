#!/usr/bin/env bash
# Run this script from inside extracted mpich2 source distribution
set -e
set -x
THISDIR=`dirname $0`
source ${THISDIR}/build-vars.sh

./configure --disable-graphics --enable-shared --enable-lib-depend --with-mpe --prefix=${MPICH_INST} CFLAGS=-fPIC CXXFLAGS=-fPIC
make -j ${MAKE_PARALLELISM}
make install
