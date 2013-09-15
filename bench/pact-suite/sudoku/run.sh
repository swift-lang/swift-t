#!/bin/sh
SUDOKU_HOME=/home/tim/ExM/exm.git/apps/sudoku/
export TURBINE_USER_LIB=$SUDOKU_HOME

SCRIPT_DIR=`cd $(dirname $0); pwd`

cd $SUDOKU_HOME/puzzles
"$SCRIPT_DIR/../scripts/opcount_bench.sh" "$1" "$2" "$SUDOKU_HOME/coord.swift" --board=100x100_116s --dfsquota=100 --boardsize=100
