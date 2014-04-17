#!/bin/bash

SWIFT=$1
OLEVELS=$2

if [ ! -f "$SWIFT" -o ! -f "$OLEVELS" ]
then
  echo "usage: $0 <Swift source file> <OLevels file>"
  echo "STC_FLAGS env var is added to stc command line"
  echo "ARGS env var is added to benchmark command line"
  exit 1
fi

n=0
while read olevel; do
  n=$(($n+1))
  echo $n: $olevel
  olevels[$n]=$olevel
done < ${OLEVELS}

PREFIX=$(basename ${SWIFT%.*})

#set -x
for i in `seq $n`; do
  olevel=${olevels[$i]}
  echo $i: ${olevel}
  O_PREFIX=${PREFIX}.olevel$i
  TCL=${O_PREFIX}.tcl
  stc ${STC_FLAGS} $olevel -C ${O_PREFIX}.ic ${STC_FLAGS} ${SWIFT} ${TCL}

  if [[ $? -ne 0 ]]; then
    echo "FAILED COMPILE $i"
    exit 1
  fi

  export TURBINE_DEBUG=0
  export ADLB_DEBUG=0
  export TURBINE_LOG=0
  export ADLB_PERF_COUNTERS=1
  export ADLB_PRINT_TIME=1
  PF=annealing-exm.olevel$i
  OUT=${O_PREFIX}.out
  COUNTS=${O_PREFIX}.counts

  # CCGrid '13 draft
  #export ADLB_SERVERS=1
  #PROCS=6
  export ADLB_SERVERS=2
  PROCS=8

  turbine -n${PROCS} $TCL $ARGS 2>&1 | tee $OUT
  if [[ $? -eq 0 ]]; then
    echo "SUCCESS"
    $(dirname $0)/opcounts.py  < $OUT > $COUNTS
  else 
    echo "ERROR!"
  fi 
  i=$(($i+1))
done < ${OLEVELS}
