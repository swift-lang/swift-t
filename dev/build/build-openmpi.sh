#!/usr/bin/env bash
# Run this script from inside extracted mpich2 source distribution
set -e

THISDIR=`dirname $0`
source ${THISDIR}/exm-settings.sh


# If CC set to mpicc, don't use it
if [ ! -z "$CC" -a "$(basename $CC)" = mpicc ]; then
  unset CC
fi

CONF_FLAGS=

./configure --prefix=${MPI_INSTALL}

make -j ${MAKE_PARALLELISM}
make install
