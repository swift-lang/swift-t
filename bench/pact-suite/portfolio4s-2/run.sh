#!/bin/sh
CODE_HOME=$HOME/ExM/rdcep.git/code/Portfolio4S-2-bench
export TURBINE_USER_LIB=$CODE_HOME

SCRIPT_DIR=`cd $(dirname $0); pwd`


#if [ "$2" = OPCOUNT ]; then
  # Only run with three procs to reduce non-determinism
#  export PACT_PAR=3 
#fi
#cd $SUDOKU_HOME/puzzles


# Case a:
ARGS="2 5 4 5 3 1 1 1 1 1 1 1 1"
# Case b:
# ARGS="2 5 4 5 1 3 3 3 3 1 1 1 1"
# Case c:
# ARGS="2 5 4 5 1 1 1 1 1 3 3 3 3"

export STC_FLAGS="-I $CODE_HOME"

"$SCRIPT_DIR/../scripts/opcount_bench.sh" "$1" "$2" "$CODE_HOME/PortCost4S.swift" $ARGS
