#!/bin/zsh
set -eu

echo run.sh $PATH
print -l $path
which turbine

./build.sh

export TURBINE_LOG=1 TURBINE_DEBUG=0 ADLB_DEBUG=0
set -x
=mpiexec -n 4 ./controller.x
