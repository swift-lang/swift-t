#!/bin/zsh

# STC RUN-TESTS

# See About.txt for notes

# Defaults
EXIT_ON_FAIL=1
MAX_TESTS=-1 # by default, unlimited
PATTERN=0
SKIP_COUNT=0
VERBOSE=0
STC_OPT_LEVEL=1 #-O level for STC
ADDTL_STC_ARGS=()

# Speed up the tests
if [ -z ${ADLB_EXHAUST_TIME} ]; then
    export ADLB_EXHAUST_TIME=1
fi

while getopts "ck:n:p:vO:t:T:" OPTION
do
  case ${OPTION}
    in
    c)
      # continue after failure
      EXIT_ON_FAIL=0
      ;;
    k)
      # skip some tests
      SKIP_COUNT=${OPTARG}
      ;;
    n)
      # run a limited number of tests
      MAX_TESTS=${OPTARG}
      ;;
    p)
      # run tests that match the pattern
      PATTERN=${OPTARG}
      ;;
    t)
      ADDTL_STC_ARGS+="-t${OPTARG}"
      ;;
    T)
      ADDTL_STC_ARGS+="-T${OPTARG}"
      ;;
    v)
      VERBOSE=1
      ;;
    O)
      STC_OPT_LEVEL=${OPTARG}
  esac
done

if [[ ${OPTION} == ":" ]]
then
  print "run-tests: arguments error!"
  exit 1
fi

if (( VERBOSE ))
then
  set -x
fi

crash()
{
  MSG=$1
  print ${MSG}
  exit 1
}

export STC_TESTS_DIR=$( cd $( dirname $0 ) ; /bin/pwd )
STC_ROOT_DIR=$( dirname $STC_TESTS_DIR )
STC_TRIES=( ${STC_ROOT_DIR}/code/bin/stc ${STC_ROOT_DIR}/bin/stc )
STC=""
for F in ${STC_TRIES}
do
  if [[ -x ${F} ]]
    then
    STC=${F}
    break
  fi
done
if [[ ${STC} == "" ]]
then
  print "Could not find STC!"
  exit 1
fi

RUN_TEST=${STC_TESTS_DIR}/run-test.zsh

compile_test()
# Translate test
{
  print "compiling: $( basename ${SWIFT_FILE} )"

  ${STC} -l ${STC_LOG_FILE} -O ${STC_OPT_LEVEL} -C ${STC_IC_FILE} \
            ${ADDTL_STC_ARGS} \
            ${SWIFT_FILE} ${TCL_FILE} \
            > ${STC_OUT_FILE} 2> ${STC_ERR_FILE}
  EXIT_CODE=${?}
}

run_test()
# Run test under Turbine/MPI
{
  SETUP_SCRIPT=${SWIFT_FILE%.swift}.setup.sh
  CHECK_SCRIPT=${SWIFT_FILE%.swift}.check.sh

  SETUP_OUTPUT=${SETUP_SCRIPT%.sh}.out
  CHECK_OUTPUT=${CHECK_SCRIPT%.sh}.out
  EXP_OUTPUT=${SWIFT_FILE%.swift}.exp
  TURBINE_OUTPUT=${TCL_FILE%.tcl}.out
  export TURBINE_OUTPUT

  ARGS=""
  ARGS_FILE=${SWIFT_FILE%.swift}.args

  # Set up the test
  if [ -x ${SETUP_SCRIPT} ]
  then
    print "executing: $( basename ${SETUP_SCRIPT} )"
    ${SETUP_SCRIPT} >& ${SETUP_OUTPUT} || return 1
  fi

  # Get test command-line arguments
  if [ -r ${ARGS_FILE} ]
  then
    read ARGS < ${ARGS_FILE}
  fi

  # Run the test
  print "running: $( basename ${TCL_FILE} )"
  ${RUN_TEST} ${TCL_FILE} ${TURBINE_OUTPUT} ${ARGS}
  EXIT_CODE=${?}

  if grep -q "THIS-TEST-SHOULD-NOT-RUN" ${SWIFT_FILE}
  then
    # This test was intended to fail at run time
    EXIT_CODE=$(( ! EXIT_CODE ))
  fi
  (( EXIT_CODE )) && return ${EXIT_CODE}

  # Check for unexecuted transforms
  grep -q "WAITING TRANSFORMS" ${TURBINE_OUTPUT}
  # This is 0 if nothing was found
  WAITING_TRANSFORMS=$(( ! ${?} ))
  if (( WAITING_TRANSFORMS ))
    then
    print "Transforms were left in the rule engine!"
    return 1
  fi

  # Check the test output with the test-specific check script
  if [ -x ${CHECK_SCRIPT} ]
  then
    print "executing: $( basename ${CHECK_SCRIPT} )"
    ${CHECK_SCRIPT} >& ${CHECK_OUTPUT} || return 1
  fi

  # Check the output for expected lines
  if [ -f ${EXP_OUTPUT} ]
  then
    local LINE_MISSING=false
    while read line
    do
      grep -q "${line}" "${TURBINE_OUTPUT}"
      if  (( $? != 0 ))
      then
        print "'${line}' wasn't present in output"
        LINE_MISSING=true
      fi
    done <  ${EXP_OUTPUT}
    if [ $LINE_MISSING = true ]; then
      return 1
    fi
  fi
  return 0
}

report_result()
{
  local EXIT_CODE=$1

  if (( EXIT_CODE == 0 ))
  then
    printf "PASSED\n\n"
  else
    printf "FAILED\n\n"
    if (( EXIT_ON_FAIL ))
      then
      print "EXIT CODE: ${EXIT_CODE}"
      exit ${EXIT_CODE}
    fi
  fi
}

TEST_COUNT=0
SWIFT_FILES=( ${STC_TESTS_DIR}/*.swift )
SWIFT_FILE_TOTAL=${#SWIFT_FILES}
for (( i=1 ; i<=SWIFT_FILE_TOTAL ; i++ ))
do
  SWIFT_FILE=${SWIFT_FILES[i]}

  if (( SKIP_COUNT ))
  then
    (( SKIP_COUNT-- ))
    continue
  fi

  if grep -q "SKIP-THIS-TEST" ${SWIFT_FILE}
  then
    continue
  fi

  if (( MAX_TESTS >= 0 && TEST_COUNT >= MAX_TESTS ))
  then
    break
  fi

  if (( ${PATTERN} ))
  then
    if [[ ! ${SWIFT_FILE} =~ ${PATTERN} ]]
      then
      continue
    fi
  fi

  (( TEST_COUNT++ ))
  TCL_FILE=${SWIFT_FILE%.swift}.tcl
  STC_OUT_FILE=${SWIFT_FILE%.swift}.stc.out
  STC_ERR_FILE=${SWIFT_FILE%.swift}.stc.err
  STC_LOG_FILE=${SWIFT_FILE%.swift}.stc.log
  STC_IC_FILE=${SWIFT_FILE%.swift}.ic

  print "test: ${TEST_COUNT} (${i}/${SWIFT_FILE_TOTAL})"
  compile_test

  COMPILE_CODE=${EXIT_CODE}

  # Reverse exit code if the case was intended to fail to compile
  if grep -q "THIS-TEST-SHOULD-NOT-COMPILE" ${SWIFT_FILE}
  then
    if grep -q "STC INTERNAL ERROR" ${STC_ERR_FILE}
    then
        :
    else
        EXIT_CODE=$(( ! EXIT_CODE ))
    fi
  fi

  if (( EXIT_CODE ))
  then
    print
    cat ${STC_OUT_FILE}
    cat ${STC_ERR_FILE}
  fi

  if (( ! COMPILE_CODE ))
  then
    run_test
    EXIT_CODE=${?}
  fi

  report_result ${EXIT_CODE}
done

print -- "--"
print "tests: ${TEST_COUNT}"

exit 0
