#!/usr/bin/env bash
set -e

# Run this script from inside extracted tcl source distribution
# E.g
#   tar xvzf tcl8.5.12-src.tar.gz
#   cd tcl8.5.12
#   ~/swift-t/dev/build/build-tcl.sh

THISDIR=`dirname $0`
source ${THISDIR}/exm-settings.sh

EXTRA_ARGS=""

if (( EXM_STATIC_BUILD )); then
  EXTRA_ARGS+=" --disable-shared"
fi

cd unix

if [ -f Makefile ]; then
  make distclean
fi

./configure --prefix=${TCL_INSTALL} ${EXTRA_ARGS}
make -j ${MAKE_PARALLELISM}
make install
