#!/bin/bash
MODE=$2
if [[ $MODE == OPCOUNT ]]; then
  # finish quickly
  ARGS="--n_epochs=1 --n_steps=1"
fi

../scripts/opcount_bench.sh $1 $MODE ~/ExM/scicolsim.git/src/annealing-exm.swift \
              --graph_file=movie_graph.txt \
              --annealingcycles=25 \
              --evoreruns=100 --reruns_per_task=1 \
              --minrange=58 --maxrange=58 \
              $ARGS
