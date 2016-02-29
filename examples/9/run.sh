#!/bin/sh
set -e

./build.sh

export TURBINE_LOG=1 TURBINE_DEBUG=0 ADLB_DEBUG=0
mpiexec -n 4 ./controller.x
