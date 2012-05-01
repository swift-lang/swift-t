#!/bin/zsh

PROGRAM_SWIFT="foreach.swift"
PROGRAM_TCL=${PROGRAM_SWIFT%.swift}.tcl

# Benchmark configuration
PROCS=${PROCS:-4}
CONTROL=${CONTROL:-2}
export TURBINE_ENGINES=$(( PROCS / CONTROL / 2 ))
export ADLB_SERVERS=$(( PROCS / CONTROL / 2 ))
TURBINE_WORKERS=$(( PROCS - TURBINE_ENGINES - ADLB_SERVERS ))

# Benchmark parameters
V=${V:-10}
NX=${V}
NY=${V}
# Delay in milliseconds
DELAY=${DELAY:-0}

# Actual amount of user work (calls to set1() or sum()):
N=$(( NX*NY*2 + NX ))

# Load common features
TURBINE=$( which turbine )
if [[ ${TURBINE} == "" ]]
then
  print "turbine not found!"
  exit 1
fi

TURBINE_HOME=$( cd $( dirname ${TURBINE} )/.. ; /bin/pwd )
source ${TURBINE_HOME}/scripts/helpers.zsh
BENCH_UTIL=$( cd $( dirname $0 )/../util ; /bin/pwd )
source ${BENCH_UTIL}/tools.zsh
exitcode

# System settings
export TURBINE_DEBUG=${TURBINE_DEBUG:-0}
export ADLB_DEBUG=${ADLB_DEBUG:-0}
export LOGGING=${LOGGING:-0}
export ADLB_EXHAUST_TIME=1
export TURBINE_USER_LIB=${BENCH_UTIL}
# Mode defaults to MPIEXEC (local execution)
MODE=mpiexec

while getopts "m:v" OPTION
  do
   case ${OPTION}
     in
     m) MODE=${OPTARG} ;;
     v) set -x         ;;
   esac
done

declare PROCS CONTROL TURBINE_ENGINES ADLB_SERVERS TURBINE_WORKERS
declare V N DELAY
declare TURBINE_HOME BENCH_UTIL

# Run stc if necessary
compile ${PROGRAM_SWIFT} ${PROGRAM_TCL}
exitcode

COMMAND="foreach.tcl --NX=${NX} --NY=${NY} --delay=${DELAY}"

source ${BENCH_UTIL}/launch.zsh
[[ ${?} == 0 ]] || return 1

# Start processing output

source ${BENCH_UTIL}/walltime.zsh
# Return error code from walltime.zsh

date_nice
