#!/bin/bash
SCS=~/ExM/scicolsim.git/

n=0
while read olevel; do
  n=$(($n+1))
  echo $n: $olevel
  olevels[$n]=$olevel
done < o-levels.txt


STC_FLAGS="-T no-engine"
#set -x
for i in `seq $n`; do
  olevel=${olevels[$i]}
  echo $i: ${olevel}
  TCL=annealing-exm.olevel$i.tcl
  stc ${STC_FLAGS} $olevel -C annealing-exm.olevel$i.ic -I $SCS/src $SCS/src/annealing-exm.swift $TCL 

  if [[ $? -ne 0 ]]; then
    echo "FAILED COMPILE $i"
    exit 1
  fi
  # FROM PACT'13 PAPER
  #ARGS='--graph_file=movie_graph.txt \
  #            --annealingcycles=25 \
  #            --evoreruns=100 --reruns_per_task=1 \
  #            --minrange=58 --maxrange=58 \
  #            --n_epochs=1 --n_steps=1'

  # CCGrid '13 draft
  #ARGS="--graph_file=${SCS}/data/movie_graph.txt \
  #            --annealingcycles=25 \
  #            --evoreruns=100 --reruns_per_task=1 \
  #            --minrange=58 --maxrange=108 --rangeinc=50 \
  #            --n_epochs=1 --n_steps=1"

  ARGS="--graph_file=${SCS}/data/movie_graph.txt \
              --annealingcycles=50 \
              --evoreruns=100 --reruns_per_task=1 \
              --minrange=58 --maxrange=108 --rangeinc=50 \
              --n_epochs=30 --n_steps=50"

  export TURBINE_USER_LIB=$SCS 
  export TURBINE_DEBUG=0
  export ADLB_DEBUG=0
  export TURBINE_LOG=0
  export ADLB_PERF_COUNTERS=1
  export ADLB_PRINT_TIME=1
  PF=annealing-exm.olevel$i
  OUT=$PF.out
  COUNTS=$PF.counts

  # CCGrid '13 draft
  #export ADLB_SERVERS=1
  #PROCS=6
  export ADLB_SERVERS=2
  PROCS=8

  turbine -n${PROCS} $TCL $ARGS 2>&1 | tee $OUT
  if [[ $? -eq 0 ]]; then
    echo "SUCCESS"
    ../scripts/opcounts.py  < $OUT > $COUNTS
  else 
    echo "ERROR!"
  fi 
  i=$(($i+1))
done < o-levels.txt
