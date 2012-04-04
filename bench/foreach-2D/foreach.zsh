#!/bin/zsh

PROGRAM_SWIFT="foreach.swift"
PROGRAM_TCL=${PROGRAM_SWIFT%.swift}.tcl

# Benchmark parameters
export TURBINE_ENGINES=1
export ADLB_SERVERS=1
TURBINE_WORKERS=1
N=1000
# Delay in milliseconds
DELAY=0

PROCS=$(( TURBINE_ENGINES + ADLB_SERVERS + TURBINE_WORKERS ))

# Load common features

TURBINE=$( which turbine )
if [[ ${TURBINE} == "" ]]
then
  print "turbine not found!"
  exit 1
fi

set -x

TURBINE_HOME=$( cd $( dirname ${TURBINE} )/.. ; /bin/pwd )
source ${TURBINE_HOME}/scripts/helpers.zsh
BENCH_UTIL=$( cd $( dirname $0 )/../util ; /bin/pwd )
source ${BENCH_UTIL}/tools.zsh
exitcode

# System settings
export TURBINE_DEBUG=0
export ADLB_DEBUG=0
export LOGGING=0
export ADLB_EXHAUST_TIME=1
# export TURBINE_USER_LIB=${BENCH_UTIL}

# Run stc if necessary
compile ${PROGRAM_SWIFT} ${PROGRAM_TCL}

START=$( date +%s )

# MODE MPIEXEC
# OUTPUT="output.txt"
# turbine -l -n ${PROCS} foreach.tcl --N=${N} --delay=${DELAY} >& ${OUTPUT}

# MODE COBALT

# LAUNCH IT
${TURBINE_COBALT} -n ${PROCS} ${PROGRAM_TCL}
exitcode "turbine-cobalt failed!"

STOP=$( date +%s )

read OUTPUT_DIR < output.txt
OUTPUT=$( ls ${OUTPUT_DIR}/*.output )
TIME=$( turbine_stats_walltime ${OUTPUT} )
if [[ ${TIME} == "" ]]
then
  print "run failed!"
  exit 1
fi

TOOK=$(( STOP - START ))
# print "TOOK: ${TOOK}"

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
