#!/bin/zsh

# Jenkins script - build only

MPICH=/tmp/mpich-install
C_UTILS=/tmp/exm-install/c-utils
ADLB=/tmp/exm-install/lb
TURBINE=/tmp/exm-install/turbine
path+=( $MPICH/bin $TURBINE/bin )

set -eu

./setup.sh

./configure --prefix=$TURBINE        \
            --with-tcl=/usr          \
            --with-mpi=$MPICH        \
            --with-c-utils=$C_UTILS  \
            --with-adlb=$ADLB        \
            --with-hdf5=no
make clean

make V=1

make V=1 install

