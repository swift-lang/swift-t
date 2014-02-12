#!/usr/bin/env bash
# Run this script from inside extracted mpich2 source distribution
set -e
set -x
THISDIR=`dirname $0`
source ${THISDIR}/exm-settings.sh

CONF_FLAGS=

if (( MPICH2_SOCK )); then
  CONF_FLAGS+=" --with-device=ch3:sock"
fi

if (( ENABLE_FORTRAN )) then
  CONF_FLAGS+=" --disable-f77 --disable-fc"
fi

./configure --disable-graphics --enable-shared --enable-lib-depend --with-mpe --prefix=${MPI_INSTALL} CFLAGS=-fPIC CXXFLAGS=-fPIC ${CONF_FLAGS}

# mpich2 build script has trouble with parallel builds
make 
make install
