#!/usr/bin/env bash
set -e
set -x
THISDIR=`dirname $0`
source ${THISDIR}/build-vars.sh

rm -rf ${TURBINE_INST}
if [ -f Makefile ]; then
    make clean
fi
./setup.sh
./configure --with-adlb=${LB_INST} \
            --with-mpi=${MPICH_INST} \
            --with-tcl=${TCL_INST} \
            --with-c-utils=${C_UTILS_INST} \
            --with-mpe \
            --prefix=${TURBINE_INST} #\
#            --disable-log
make package -j ${MAKE_PARALLELISM}
make install
#make test_results
