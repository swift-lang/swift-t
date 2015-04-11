#!/bin/bash
SCS=~/ExM/scicolsim.git/

export STC_FLAGS="-I $SCS/src"

export SWIFT_PATH=$SCS/lib

export PATH=$PATH:~/ExM/inst/stc/bin:~/ExM/inst/turbine/bin

ARGS="--graph_file=${SCS}/data/movie_graph.txt \
            --annealingcycles=1 \
            --evoreruns=100 --reruns_per_task=1 \
            --minrange=58 --maxrange=108 --rangeinc=50 \
            --n_epochs=30 --n_steps=1"

export ARGS

../scripts/o-level-test.sh $SCS/src/annealing-exm.swift ../scripts/o-level-takeone.txt ./o-level-takeone-out
