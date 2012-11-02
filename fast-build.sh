#!/usr/bin/env bash
# Fast build script that avoids rebuilding.  May not work if configure
# files have changed

set -e
THISDIR=`dirname $0`

BUILDVARS=${THISDIR}/build-vars.sh
if [ ! -f ${BUILDVARS} ] ; then
  echo "Need ${BUILDVARS} to exist"
  exit 1
fi
source ${BUILDVARS}


cd ${C_UTILS}
echo
echo "Building c-utils"
echo "================"
make && make install

cd ${LB}
echo
echo "Building lb"
echo "================"
make && make install

cd ${TURBINE}
echo
echo "Building Turbine"
echo "================"
make && make install

cd ${STC}
echo
echo "Building STC"
echo "================"
ant
