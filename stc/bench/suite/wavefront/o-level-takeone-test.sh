#!/bin/bash

export PATH=$PATH:~/ExM/inst/stc/bin:~/ExM/inst/turbine/bin

export TURBINE_USER_LIB=$(dirname $0)/lib

ARGS="-N=400 --mu=-15 --sigma=1"

export ARGS

../scripts/o-level-test.sh ./wavefront.swift ../scripts/o-level-takeone.txt ./o-level-takeone-out
