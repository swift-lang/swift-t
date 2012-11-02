#!/usr/bin/env bash

set -e
THISDIR=`dirname $0`

BUILDVARS=${THISDIR}/build-vars.sh
if [ ! -f ${BUILDVARS} ] ; then
  echo "Need ${BUILDVARS} to exist"
  exit 1
fi
source ${BUILDVARS}

cd ${TURBINE}
echo
echo "Testing Turbine"
echo "================"
make test_results

cd ${STC}
echo
echo "Testing STC"
echo "================"
# Test at multiple optimization levels
../tests/run-tests.zsh -c -O0 -O1 -O2

