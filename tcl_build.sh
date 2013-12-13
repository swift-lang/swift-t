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
  # Setup C compiler for static build on cray
  export CC=cc

  # Tclsh needs to export dynamic symbols.  We can't get this to work
  # with static system libraries without modifying the Tcl build scripts,
  # so we will dynamically link tclsh even though we intend to later
  # build static binaries with the libtcl*.a static library.
  export LDFLAGS="-dynamic"
fi

cd unix

if [ -f Makefile ]; then
  make distclean
fi

./configure --prefix=${TCL_INST} ${EXTRA_ARGS}
make -j ${MAKE_PARALLELISM}
make install
