#!/bin/bash

LOG=LOOPER_LOG
LOOPS=100
[ ! -z $1 ] &&  LOOPS=$1
rm $LOG

for count in `seq 1 1 $LOOPS`
do
    echo "=================== RUN $count ======================="
    #./run-test.sh coaster-std-out-err.swift 2>&1  | tee -a $LOG
    ./run-test.sh all 2>&1  | tee -a $LOG
done