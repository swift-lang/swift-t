#!/bin/bash

export STC_FLAGS="-T no-engine"

export TURBINE_USER_LIB=$(dirname $0)/lib

ARGS="--n=24 --sleeptime=0"

export ARGS

../scripts/o-level-test.sh ./fib.swift ./rc-o-levels.txt ./rc-o-levels-out
