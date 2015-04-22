#!/usr/bin/env bash
set -e

if [[ ! $# -eq 3 ]]; then
  echo "usage $0 <n> <m> <sleeptime>"
  exit 1
fi

N=$1
M=$2
sleeptime=$3

prefix=embarrassing._N_${N}__M_${M}__sleeptime_${sleeptime}.ADLB 
export ADLB_PRINT_TIME=1
export ADLB_PERF_COUNTERS=1
export ADLB_DEBUG=0
export TURBINE_DEBUG=0

mpiexec -n 8 ./embarrassing ${N} ${M} ${sleeptime} &> $prefix.out

../scripts/opcounts.py $prefix.out | tee $prefix.counts
