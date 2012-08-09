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
EXITCODE=${?}
[[ ${EXITCODE} != 0 ]] && exit ${EXITCODE}

# Valgrind-related checks:
grep -f ${STC_TESTS_DIR}/valgrind-patterns.grep ${OUTPUT}
if [[ ${?} == 0 ]]
then
  print "run-test: valgrind detected error: ${PROGRAM}"
  exit 1
fi

exit ${EXITCODE}
