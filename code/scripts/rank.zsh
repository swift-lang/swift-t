#!/bin/zsh

# Get the output only from the given rank
# Assumes the output file is formatted via "mpiexec -l"

RANK=$1
FILE=$2

grep "\[${RANK}\]" < ${FILE} | less

