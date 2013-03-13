#!/bin/sh
export TURBINE_USER_LIB=/home/tga/ExM/exm.git/apps/sudoku/
../scripts/opcount_bench.sh $1 $2 ~/ExM/exm.git/apps/sudoku/coord.swift --board=100x100_99s --dfsquota=1000
