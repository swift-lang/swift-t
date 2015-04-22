#!/usr/bin/env bash

set -e
THISDIR=`dirname $0`

BUILDVARS=${THISDIR}/exm-settings.sh
if [ ! -f ${BUILDVARS} ] ; then
  echo "Need ${BUILDVARS} to exist"
  exit 1
fi
source ${BUILDVARS}

cd ${TURBINE}
echo
echo "Testing Turbine"
echo "================"
if make -k test_results
then
    echo "All Turbine tests passed"
else
    echo "Turbine tests failed"
fi

cd ${STC}
echo
echo "Testing STC"
echo "================"
# Test at multiple optimization levels
../tests/run-tests.zsh -c -O0 -O1 -O2 -O3

