#!/bin/zsh

PROGRAM_SWIFT="foreach.swift"
PROGRAM_TCL=${PROGRAM_SWIFT%.swift}.tcl

# Benchmark parameters
PROCS=${PROCS:-4}
CONTROL=${CONTROL:-2}
export TURBINE_ENGINES=$(( PROCS / CONTROL / 2 ))
export ADLB_SERVERS=$(( PROCS / CONTROL / 2 ))
TURBINE_WORKERS=$(( PROCS - TURBINE_ENGINES - ADLB_SERVERS ))
N=${N:-1000}
# Delay in milliseconds
DELAY=${DELAY:-0}

# Load common features

TURBINE=$( which turbine )
if [[ ${TURBINE} == "" ]]
then
  print "turbine not found!"
  return 1
fi

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
export TURBINE_USER_LIB=${BENCH_UTIL}
# Mode defaults to MPIEXEC (local execution)
MODE=mpiexec

while getopts "m:" OPTION
  do
   case ${OPTION}
     in
     m) MODE=${OPTARG} ;;
     v) set -x         ;;
   esac
done

# Log all settings
declare PROCS CONTROL TURBINE_ENGINES ADLB_SERVERS TURBINE_WORKERS
declare N DELAY
declare TURBINE_HOME BENCH_UTIL

# Run stc if necessary
compile ${PROGRAM_SWIFT} ${PROGRAM_TCL}

START=$( date +%s )

# Launch it
case ${MODE}
  in
  "mpiexec")
    OUTPUT="turbine-output.txt"
    OUTPUT_DIR=${PWD}
    turbine -l -n ${PROCS} \
      foreach.tcl --N=${N} --delay=${DELAY} >& ${OUTPUT}
    exitcode "turbine failed!"
    ;;
  "cobalt")
    # User must edit Tcl to add user libs
    unset TURBINE_USER_LIB
    OUTPUT_TOKEN_FILE=$( mktemp )
    ${TURBINE_COBALT} -d ${OUTPUT_TOKEN_FILE} \
      -n ${PROCS} ${PROGRAM_TCL} --N=${N} --delay=${DELAY}
    exitcode "turbine-cobalt failed!"
    read OUTPUT_DIR < ${OUTPUT_TOKEN_FILE}
    declare OUTPUT_DIR
    rm ${OUTPUT_TOKEN_FILE}
    OUTPUT=$( ls ${OUTPUT_DIR}/*.output )
    ;;
  *)
    print "unknown MODE: ${MODE}"
    return 1
    ;;
esac
STOP=$( date +%s )

TOOK=$(( STOP - START ))
print "TOOK: ${TOOK}"

# Start processing output

float -F 3 TIME TOTAL_TIME TOTAL_RATE WORKER_RATE UTIL

if grep -qi abort ${OUTPUT}
then
  print "run aborted!"
  return 1
fi
TIME=$( turbine_stats_walltime ${OUTPUT} )
if [[ ${TIME} == "" ]]
then
  print "run failed!"
  return 1
fi

# Collect stats:
{
  TIME=$(( TIME - ADLB_EXHAUST_TIME ))
  print "N: ${N}"
  print "TIME: ${TIME}"
  if (( TIME ))
  then
    TOTAL_RATE=$(( N / TIME ))
    print "TOTAL_RATE: ${TOTAL_RATE}"
    WORKER_RATE=$(( N / TIME / TURBINE_WORKERS ))
    print "WORKER_RATE: ${WORKER_RATE}"
  fi
  if (( DELAY ))
  then
    WORK_TIME=$(( N * DELAY/1000 ))
    TOTAL_TIME=$(( TIME * TURBINE_WORKERS ))
    UTIL=$(( WORK_TIME / TOTAL_TIME ))
    print "UTIL: ${UTIL}"
  fi
} | tee ${OUTPUT_DIR}/stats.txt

return 0
