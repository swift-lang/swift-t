#!/bin/bash

INST=$HOME/ExM/inst
LB=$INST/lb
CUTILS=$INST/c-utils
MPICH=$INST/mpich2

mpic++ -O2 -std=c++0x -L $LB/lib -L $CUTILS/lib -I $MPICH/include -I $LB/include -I $CUTILS/include -o fib fib.cpp -ladlb -lexmcutils -Wl,-rpath,$LB/lib,-rpath,$CUTILS/lib
