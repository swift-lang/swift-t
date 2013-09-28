#!/usr/bin/env bash
set -e
set -x
THISDIR=`dirname $0`
source ${THISDIR}/build-vars.sh

rm -rf ${TURBINE_INST}
if [ -f Makefile ]; then
    make clean
fi

if [ -z "$SKIP_AUTOTOOLS" ]; then
  rm -rf ./config.status ./autom4te.cache
  ./setup.sh
fi

EXTRA_ARGS=
if [ ! -z "$EXM_OPT_BUILD" ]; then
    EXTRA_ARGS+="--enable-fast"
fi

if [ ! -z "$ENABLE_MPE" ]; then
    EXTRA_ARGS+="--with-mpe"
fi

if [ ! -z "$EXM_CRAY" ] ; then
    export CC=gcc
    export CFLAGS="-g -O2"
    EXTRA_ARGS+="--enable-custom-mpi"
fi

if [ ! -z "$WITH_PYTHON" ] ; then
  EXTRA_ARGS+="--enable-python --with-python=${WITH_PYTHON}"
fi

if [ ! -z "$TCL_VERSION" ] ; then
  EXTRA_ARGS+="--with-tcl-version=$TCL_VERSION"
fi

./configure --with-adlb=${LB_INST} \
            ${CRAY_ARGS} \
            --with-mpi=${MPICH_INST} \
            --with-tcl=${TCL_INST} \
            --with-c-utils=${C_UTILS_INST} \
            --prefix=${TURBINE_INST} \
            ${EXTRA_ARGS}
#            --disable-log
make package -j ${MAKE_PARALLELISM}
make install
#make test_results
