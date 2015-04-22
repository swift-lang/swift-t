#!/bin/zsh

PROGRAM_SWIFT="loops.swift"
PROGRAM_TCL=${PROGRAM_SWIFT%.swift}.tcl

# Benchmark parameters
PROCS=${PROCS:-4}
TURBINE_ENGINES_DEFAULT=$(( PROCS / 4 ))
ADLB_SERVERS_DEFAULT=$(( PROCS / 4 ))
export TURBINE_ENGINES=${TURBINE_ENGINES:-${TURBINE_ENGINES_DEFAULT}}
export ADLB_SERVERS=${ADLB_SERVERS:-${ADLB_SERVERS_DEFAULT}}
TURBINE_WORKERS=$(( PROCS - TURBINE_ENGINES - ADLB_SERVERS ))
V=${V:-10}
N=$(( V ** 4 ))

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
export ADLB_EXHAUST_TIME=1
export TURBINE_USER_LIB=${BENCH_UTIL}
# Mode defaults to MPIEXEC (local execution)
MODE=cobalt

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
declare V N

COMMAND="loops.tcl --V=${V}"

source ${BENCH_UTIL}/launch.zsh
[[ ${?} == 0 ]] || return 1

# Start processing output

# source ${BENCH_UTIL}/walltime.zsh
# Return error code from walltime.zsh
# CODE=${?}

declare TURBINE_OUTPUT
clog ${TURBINE_OUTPUT}/adlb.clog2
export MPE_EVENTS="ADLB_wkr_put ADLB_wkr_get"
first-last.x ${TURBINE_OUTPUT}/adlb.clog2.txt -1

date_nice
return 0
