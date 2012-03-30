#!/bin/zsh

# Benchmark parameters
export TURBINE_ENGINES=1
export ADLB_SERVERS=1
TURBINE_WORKERS=1
PROCS=$(( TURBINE_ENGINES + ADLB_SERVERS + TURBINE_WORKERS ))
N=1000
# Delay in milliseconds
DELAY=0

# System settings
export TURBINE_DEBUG=0
export ADLB_DEBUG=0
export LOGGING=0
export ADLB_EXHAUST_TIME=1
export TURBINE_USER_LIB=$( cd ${PWD}/../util ; /bin/pwd )

START=$( date +%s )
turbine -l -n ${PROCS} foreach.tcl --N=${N} --delay=${DELAY}
STOP=$( date +%s )

TIME=$(( STOP - START - ADLB_EXHAUST_TIME ))

print "N: ${N} TIME: ${TIME}"

if (( TIME ))
then
  TOTAL_RATE=$(( N / TIME ))
  print "TOTAL_RATE: ${TOTAL_RATE}"
  WORKER_RATE=$(( N / TIME / TURBINE_WORKERS ))
  print "WORKER_RATE: ${WORKER_RATE}"
fi

if (( ${DELAY} ))
then
  WORK_TIME=$(( N * DELAY/1000 ))
  TOTAL_TIME=$(( TIME * TURBINE_WORKERS ))
  UTIL=$(( WORK_TIME / TOTAL_TIME ))
  print "UTIL: ${UTIL}"
fi
