#!/bin/bash

INST=$HOME/ExM/inst
LB=$INST/lb
CUTILS=$INST/c-utils

mpicc -std=c99 -O2 -L $LB/lib -L $CUTILS/lib -I $LB/include -I $CUTILS/include -o embarrassing embarrassing.c -ladlb -lexmcutils -Wl,-rpath,$LB/lib,-rpath,$CUTILS/lib
