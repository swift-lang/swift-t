#!/bin/zsh -f

set -eu

# usage: run-test <OPTIONS> <PROGRAM> <OUTPUT>
# TURBINE_HOME must be set to a valid Turbine install, or turbine must be
# in the path
# Set VALGRIND=/path/to/valgrind to run valgrind (Turbine feature)

# Defaults:
VERBOSE=0
TURBINE_VERBOSE=""

while getopts "V" OPTION
do
  case ${OPTION}
    in
    V)
      VERBOSE=1
      ;;
    *)
      # ZSH already prints an error message
      exit 1
  esac
done

if (( VERBOSE ))
then
  set -x
  TURBINE_VERBOSE=-V
fi

shift $(( OPTIND-1 ))

PROGRAM=$1
OUTPUT=$2
shift 2
ARGS=( ${*} )

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

if (( ${+TURBINE_HOME} ))
then
  TURBINE=${TURBINE_HOME}/bin/turbine
  if [[ ! -x ${TURBINE} ]]
  then
    print "Bad TURBINE_HOME!"
    print "Not executable: ${TURBINE}"
    exit 1
  fi
fi

# Look for Turbine in PATH
if (( ! ${+TURBINE} ))
then
  TURBINE=$( which turbine )
  if [[ ! -x ${TURBINE} ]]
  then
    print "Could not locate turbine on path"
    exit 1
  fi
fi
  
# Allow user to override these from environment
export ADLB_SERVERS=${TEST_ADLB_SERVERS:-1}
WORKERS=${TEST_ADLB_WORKERS:-1}

PROCS=$(( ADLB_SERVERS + WORKERS ))

# Setup environment variables to get info out of ADLB
export ADLB_PERF_COUNTERS=${ADLB_PERF_COUNTERS:-true}
export ADLB_PRINT_TIME=true

# Run Turbine:
TURBINE_ARGS="-l ${TURBINE_VERBOSE} -n ${PROCS}"
${TURBINE} ${=TURBINE_ARGS} ${PROGRAM} ${ARGS} >& ${OUTPUT}

# Valgrind-related checks:
if (( ${#VALGRIND} ))
then
  if grep -f ${STC_TESTS_DIR}/valgrind-patterns.grep ${OUTPUT}
  then
    print "run-test: valgrind detected error: ${PROGRAM}"
    exit 1
  fi
fi

# All errors cause early exit
exit 0
