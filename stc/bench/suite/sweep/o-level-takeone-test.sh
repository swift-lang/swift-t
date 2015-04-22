#!/bin/bash

export PATH=$PATH:~/ExM/inst/stc/bin:~/ExM/inst/turbine/bin

export TURBINE_USER_LIB=$(dirname $0)/lib

export ARGS="--mu=-9 --sigma=1 --M=1000 --N=100"

../scripts/o-level-test.sh ./embarrassing_lognorm.swift ../scripts/o-level-takeone.txt ./o-level-takeone-out
