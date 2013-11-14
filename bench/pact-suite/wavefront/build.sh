#!/bin/bash
INST=$HOME/ExM/inst
LB=$INST/lb
CUTILS=$INST/c-utils
MPICH=$INST/mpich2

mpicc -O2 -Wall -std=c99 -L $LB/lib -L $CUTILS/lib -I $MPICH/include -I $LB/include -I $CUTILS/include -o wavefront wavefront.c -ladlb -lexmcutils -Wl,-rpath,$LB/lib,-rpath,$CUTILS/lib
