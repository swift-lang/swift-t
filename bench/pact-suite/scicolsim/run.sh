#!/bin/bash
MODE=$2
if [[ $MODE == OPCOUNT ]]; then
  # finish quickly
  ARGS="--n_epochs=1 --n_steps=1"
fi


ARGS+=" --graph_file=movie_graph.txt \
      --annealingcycles=25 \
      --evoreruns=100 --reruns_per_task=1 \
      --minrange=58 --maxrange=108 --rangeinc=50"

# FROM PACT'13 PAPER:
#ARGS+=" --graph_file=movie_graph.txt \
#      --annealingcycles=25 \
#      --evoreruns=100 --reruns_per_task=1 \
#      --minrange=58 --maxrange=58"

../scripts/opcount_bench.sh $1 $MODE ~/ExM/scicolsim.git/src/annealing-exm.swift \
              $ARGS
