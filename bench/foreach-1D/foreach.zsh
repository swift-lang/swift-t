#!/bin/zsh

export ADLB_EXHAUST_TIME=1
export TURBINE_USER_LIB=$( cd ${PWD}/../util ; /bin/pwd )
turbine -l -n 3 foreach.tcl --N=2 --delay=10
