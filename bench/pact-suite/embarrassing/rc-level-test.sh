#!/bin/bash

export STC_FLAGS="-T no-engine"

export TURBINE_USER_LIB=$(dirname $0)/lib

ARGS="--mu=-9 --sigma=1 --M=1000 --N=100"

export ARGS

../scripts/o-level-test.sh ./embarrassing_lognorm.swift ./rc-o-levels.txt ./rc-o-levels-out
