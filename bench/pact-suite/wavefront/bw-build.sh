#!/bin/bash
INST=$HOME/soft/exm-dev
LB=$INST/lb
CUTILS=$INST/c-utils

MPICH_INST=/opt/cray/mpt/default/gni/mpich2-gnu/48/

gcc -O2 -Wall -std=c99 -L $LB/lib -L $CUTILS/lib -L $MPICH_INST/lib -I $MPICH_INST/include -I $LB/include -I $CUTILS/include -o wavefront wavefront.c -ladlb -lmpich -lexmcutils -Wl,-rpath,$LB/lib,-rpath,$CUTILS/lib,-rpath,$MPICH_INST/lib
