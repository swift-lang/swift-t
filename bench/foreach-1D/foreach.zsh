#!/bin/zsh

PROGRAM_SWIFT="foreach.swift"
PROGRAM_TCL=${PROGRAM_SWIFT%.swift}.tcl

# Benchmark parameters
PROCS=${PROCS:-8192}
CONTROL=${CONTROL:-64}
export TURBINE_ENGINES=$(( PROCS / CONTROL / 2 ))
export ADLB_SERVERS=$(( PROCS / CONTROL / 2 ))
TURBINE_WORKERS=$(( PROCS - TURBINE_ENGINES - ADLB_SERVERS ))
N=$(( TURBINE_WORKERS ))  # N*10 # ${N:-10}
# Delay in milliseconds
DELAY=${DELAY:-100000}

export WALLTIME="00:20:00"

# Load common features

TURBINE=$( which turbine )
if (( ${?} ))
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
export TURBINE_DEBUG=${TURBINE_DEBUG:-0}
export ADLB_DEBUG=${ADLB_DEBUG:-0}
export TURBINE_LOG=${TURBINE_LOG:-0}
export ADLB_EXHAUST_TIME=5
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
declare TURBINE_HOME BENCH_UTIL
declare N DELAY

# Run stc if necessary
stc -u ${PROGRAM_SWIFT} ${PROGRAM_TCL}
exitcode

COMMAND="foreach.tcl --N=${N} --delay=${DELAY}"

source ${BENCH_UTIL}/launch.zsh
exitcode

# Start processing output

source ${BENCH_UTIL}/walltime.zsh
# Return error code from walltime.zsh
