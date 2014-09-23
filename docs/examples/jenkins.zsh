#!/bin/zsh

set -eu

C_UTILS=/tmp/exm-install/c-utils
TURBINE=/tmp/exm-install/turbine
STC=/tmp/exm-install/stc
MPICH=/tmp/mpich-install
path+=( $MPICH/bin $TURBINE/bin $STC/bin )

print HI
./run.sh
