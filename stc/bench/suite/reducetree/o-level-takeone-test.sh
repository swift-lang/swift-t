#!/bin/bash

export PATH=$PATH:~/ExM/inst/stc/bin:~/ExM/inst/turbine/bin

export ARGS="--n=24 --sleeptime=0"

../scripts/o-level-test.sh ./fib.swift ../scripts/o-level-takeone.txt ./o-level-takeone-out
