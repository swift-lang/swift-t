#!/bin/bash

export STC_FLAGS="-T no-engine"

export TURBINE_USER_LIB=$(dirname $0)/lib

ARGS="-N=400 --mu=-15 --sigma=1"

export ARGS

../scripts/o-level-test.sh ./wavefront.swift ./rc-o-levels.txt ./rc-o-levels-out
