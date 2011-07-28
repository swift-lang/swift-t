#!/bin/zsh

# Get the output only from the given rank
# Assumes the output file is formatted via "mpiexec -l"

RANK=$1
REGEXP=$2
FILE=$3

grep "\[${RANK}\]" ${REGEXP} ${FILE}
