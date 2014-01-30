#!/bin/bash

INST=$HOME/soft/exm-dev
LB=$INST/lb
CUTILS=$INST/c-utils

MPICH_INST=/opt/cray/mpt/default/gni/mpich2-gnu/48/

gcc -std=c99 -O2 -L $LB/lib -L $CUTILS/lib -L $MPICH_INST/lib -I $LB/include -I $CUTILS/include -I $MPICH_INST/include -o embarrassing embarrassing.c -lmpich -ladlb -lexmcutils -Wl,-rpath,$LB/lib,-rpath,$CUTILS/lib,-rpath,$MPICH_INST/lib
