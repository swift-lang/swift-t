#!/bin/sh
set -eu

which turbine

THIS=$( readlink --canonicalize $( dirname $0 ) )
cd $THIS

./build.sh

export TURBINE_LOG=1 TURBINE_DEBUG=0 ADLB_DEBUG=0
set -x
which mpiexec
mpiexec -n 4 ./controller.x
