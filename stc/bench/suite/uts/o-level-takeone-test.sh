#!/bin/bash

export PATH=$PATH:~/ExM/inst/stc/bin:~/ExM/inst/turbine/bin

export TURBINE_USER_LIB=$(dirname $0)/lib

ARGS="--gen_mx=20"

export ARGS

../scripts/o-level-test.sh ./uts.swift ../scripts/o-level-takeone.txt ./o-level-takeone-out
