#!/bin/zsh -f

# usage: run-test <OPTIONS> <PROGRAM> <OUTPUT>
# turbine must be in your PATH or in TURBINE
#         or installed in TURBINE_HOME or TURBINE_INSTALL
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

if [[ ${TURBINE_HOME} != "" ]]
then
  TURBINE=${TURBINE_HOME}/bin/turbine
  if [[ ! -x ${TURBINE} ]]
  then
    print "Bad TURBINE_HOME!"
    print "Not executable: ${TURBINE}"
    exit 1
  fi
fi

if [[ ${TURBINE_INSTALL} != "" ]]
then
  TURBINE=${TURBINE_INSTALL}/bin/turbine
  if [[ ! -x ${TURBINE} ]]
  then
    print "Bad TURBINE_INSTALL!"
    print "Not executable: ${TURBINE}"
    exit 1
  fi
fi

# Look for Turbine in PATH
if [[ ${TURBINE} == "" ]]
  then
  TURBINE=$( which turbine )
fi

# Allow user to override these from environment
ENGINES=${TEST_TURBINE_ENGINES:-1}
SERVERS=${TEST_ADLB_SERVERS:-1}
WORKERS=${TEST_ADLB_WORKERS:-1}

PROCS=$(( ENGINES + SERVERS + WORKERS ))

# Setup environment variables to get info out of ADLB
export ADLB_PERF_COUNTERS=${ADLB_PERF_COUNTERS:-true}
export ADLB_PRINT_TIME=true

# Setup environment vars for Turbine size
export TURBINE_ENGINES=${ENGINES}
export ADLB_SERVERS=${SERVERS}

# Run Turbine:
TURBINE_ARGS="-l ${TURBINE_VERBOSE} -n ${PROCS}"
${TURBINE} ${=TURBINE_ARGS} ${PROGRAM} ${ARGS} >& ${OUTPUT}
EXITCODE=${?}
[[ ${EXITCODE} != 0 ]] && exit ${EXITCODE}

# Valgrind-related checks:
grep -f ${STC_TESTS_DIR}/valgrind-patterns.grep ${OUTPUT}
if [[ ${?} == 0 ]]
then
  print "run-test: valgrind detected error: ${PROGRAM}"
  exit 1
fi

# All errors cause early exit
exit 0
