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

if [ ! -z "$EXM_CRAY" ]; then
    export CC=gcc
fi

cd unix
./configure --prefix=${TCL_INST} 
make -j ${MAKE_PARALLELISM}
make install
