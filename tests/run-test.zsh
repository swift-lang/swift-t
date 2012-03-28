#!/bin/zsh

# usage: run-test <PROGRAM> <OUTPUT>
# turbine must be in your PATH or in TURBINE
# Set VALGRIND to run valgrind

PROGRAM=$1
OUTPUT=$2
shift 2
ARGS=${*}

if [[ ${PROGRAM} == "" ]]
then
  print "Not given: PROGRAM"
  exit 1
fi

if [[ ${OUTPUT} == "" ]]
then
  print "Not given: OUTPUT"
  exit 1
fi

if [[ ${TURBINE} == "" ]]
  then
  TURBINE=$( which turbine )
fi

# TODO: allow user to override these from environment
ENGINES=1
SERVERS=1
WORKERS=1

PROCS=$(( ENGINES + SERVERS + WORKERS ))

${TURBINE} -l -n ${PROCS} ${PROGRAM} ${ARGS} >& ${OUTPUT}
# Return result from mpiexec
