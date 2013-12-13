#!/usr/bin/env bash
# Run this script from inside extracted tcl source distribution
# E.g
#   tar xvzf tcl8.5.12-src.tar.gz 
#   cd tcl8.5.12
#   ~/exm/sfw/dev/tcl_build.sh
set -e
set -x
THISDIR=`dirname $0`
source ${THISDIR}/build-vars.sh

EXTRA_ARGS=""

if (( EXM_STATIC_BUILD )); then
  EXTRA_ARGS+=" --disable-shared"
fi

if (( EXM_CRAY && EXM_STATIC_BUILD )); then
  # Setup C compiler for static buildon cray
  export CC=cc

  # TODO: can't do full static build yet since tcl wants to --export-dynamic
  #export LDFLAGS="-static"
fi

cd unix

if [ -f Makefile ]; then
  make distclean
fi

./configure --prefix=${TCL_INST} ${EXTRA_ARGS}
make -j ${MAKE_PARALLELISM}
make install
