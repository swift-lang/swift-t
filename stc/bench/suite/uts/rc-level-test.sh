#!/bin/bash

export STC_FLAGS="-T no-engine"

export TURBINE_USER_LIB=$(dirname $0)/lib

ARGS="--gen_mx=20"

export ARGS

../scripts/o-level-test.sh ./uts.swift ./rc-o-levels.txt ./rc-o-levels-out
