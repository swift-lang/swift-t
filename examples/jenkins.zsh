#!/bin/zsh
set -eu

C_UTILS=/tmp/exm-install/c-utils
TURBINE=/tmp/exm-install/turbine
STC=/tmp/exm-install/stc

PATH=${TURBINE}/bin:${PATH}
PATH=${STC}/bin:${PATH}

print "Settings:"
which mpicc mpiexec swift-t stc turbine

print

source install-blas.sh
source install-fortwrap.sh

# Run the examples suite:
./run.sh
