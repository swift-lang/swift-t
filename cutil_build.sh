#!/usr/bin/env bash
set -e
set -x

THISDIR=`dirname $0`
source ${THISDIR}/build-vars.sh

set -e
if [ -f Makefile ]; then
    make clean
fi
./setup.sh
./configure --enable-shared --prefix=${C_UTILS_INST}
make
make install
