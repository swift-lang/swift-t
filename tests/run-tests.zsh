#!/bin/zsh -f

# STC RUN-TESTS

# See About.txt for notes

# Defaults
EXIT_ON_FAIL=1
MAX_TESTS=-1 # by default, unlimited
PATTERN=""
SKIP_COUNT=0

COMPILE_ONLY=0
RUN_DISABLED=0
# If 1, show error outputs
REPORT_ERRORS=0
VERBOSE=0
STC_OPT_LEVELS=() #-O levels to test for STC
DEFAULT_STC_OPT_LEVEL=2
ADDTL_STC_ARGS=()
LEAK_CHECK=1
STC_TESTS_OUT_DIR=

# Speed up the tests
if [ -z ${ADLB_EXHAUST_TIME} ]; then
    export ADLB_EXHAUST_TIME=1
fi

while getopts "cCDek:n:p:VO:t:T:alo:" OPTION
do
  case ${OPTION}
    in
    c)
      # continue after failure
      EXIT_ON_FAIL=0
      ;;
    C)
      # compile only
      COMPILE_ONLY=1
      ;;
    D)
      #Run disabled tests
      RUN_DISABLED=1
      ;;
    e)
      # Show error outputs
      REPORT_ERRORS=1
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
      # run only tests that match the pattern
      PATTERN=${OPTARG}
      ;;
    t)
      ADDTL_STC_ARGS+="-t${OPTARG}"
      ;;
    T)
      ADDTL_STC_ARGS+="-T${OPTARG}"
      ;;
    V)
      VERBOSE=1
      ;;
    O)
      STC_OPT_LEVELS+=${OPTARG}
      ;;
    l)
      LEAK_CHECK=0
      ;;
    o)
      if [ ! -d ${OPTARG} ]; then
        echo "${OPTARG} is not a directory"
        exit 1
      fi
      STC_TESTS_OUT_DIR=$(cd ${OPTARG}; pwd)
      ;;
    *)
      # ZSH already prints an error message
      exit 1
  esac
done

if (( VERBOSE ))
then
  set -x
fi

if (( LEAK_CHECK ))
then
  # Force reporting of leaks by ADLB
  export ADLB_REPORT_LEAKS=true
fi

if [ ${#STC_OPT_LEVELS} -eq 0 ]
then
  STC_OPT_LEVELS=($DEFAULT_STC_OPT_LEVEL)
fi

crash()
{
  MSG=$1
  print ${MSG}
  exit 1
}

export STC_TESTS_DIR=$( cd $( dirname $0 ) ; /bin/pwd )
STC_ROOT_DIR=$( dirname $STC_TESTS_DIR )
STC_TRIES=( ${STC_ROOT_DIR}/code ${STC_ROOT_DIR} )
STC=""
for D in ${STC_TRIES}
do
  if [[ -x ${D}/bin/stc && -r ${D}/conf/stc-env.sh ]]
    then
    STC=${D}/bin/stc
    break
  fi
done
if [[ ${STC} == "" ]]
then
  STC=$( which stc )
  [[ ${?} != 0 ]] && STC=""
fi
if [[ ${STC} == "" ]]
then
  print "Could not find STC!"
  exit 1
fi
print "using stc: ${STC}\n"

RUN_TEST=${STC_TESTS_DIR}/run-test.zsh

# Output dir for tests
export STC_TESTS_OUT_DIR=${STC_TESTS_OUT_DIR:-$STC_TESTS_DIR}
mkdir -p ${STC_TESTS_OUT_DIR}

export TURBINE_USER_LIB=${STC_TESTS_DIR}

which tclsh > /dev/null
if [[ ${?} != 0 ]]
then
  print "Could not find tclsh!"
  exit 1
fi

# Make the package for tests with builtins
tclsh ${STC_TESTS_DIR}/make-package.tcl > ${STC_TESTS_DIR}/pkgIndex.tcl

compile_test()
# Translate test
{
  local STC_OPT_LEVEL=$1
  print "compiling: $( basename ${SWIFT_FILE} ) at O${STC_OPT_LEVEL}"

  local ARGS=""
  if [ -f "${STC_ARGS}" ]
  then
    ARGS=$(cat ${STC_ARGS})
  fi

  pushd $STC_TESTS_DIR
  ${STC} -l ${STC_LOG_FILE} -O ${STC_OPT_LEVEL} -C ${STC_IC_FILE} \
            ${ADDTL_STC_ARGS} ${ARGS} \
            ${SWIFT_FILE} ${TCL_FILE} \
            > ${STC_OUT_FILE} 2> ${STC_ERR_FILE}
  EXIT_CODE=${?}
  popd
}

run_test()
# Run test under Turbine/MPI
{
  # Run program, check and setup scripts with test directory as
  # working directory
  SETUP_SCRIPT=${TEST_NAME}.setup.sh
  CHECK_SCRIPT=${TEST_NAME}.check.sh

  SETUP_OUTPUT=${TCL_FILE%.tcl}.setup.out
  CHECK_OUTPUT=${TCL_FILE%.tcl}.check.out
  EXP_OUTPUT=${TEST_PATH}.exp
  TURBINE_OUTPUT=${TCL_FILE%.tcl}.out
  TURBINE_XPT_RELOAD_OUTPUT=${TCL_FILE%.tcl}.reload.out
  export TURBINE_XPT_RELOAD_OUTPUT

  ARGS=""
  ARGS_FILE=${TEST_PATH}.args

  # Export output filenames for check script
  export TURBINE_OUTPUT STC_OUT_FILE STC_ERR_FILE STC_LOG_FILE


  # Get test command-line arguments
  if [ -r ${ARGS_FILE} ]
  then
    read ARGS < ${ARGS_FILE}
  fi

  # Run the test from within the test directory
  pushd $STC_TESTS_DIR

  # Run in subshell to allow setting environment variables without
  # affecting other tests.  Return values 0=OK, 1=TEST FAILED, 2=SETUP FAILED
  (
    if [ -f ${STC_TESTS_DIR}/${SETUP_SCRIPT} ]
    then
      print "sourcing:  $( basename ${SETUP_SCRIPT} )"
      source ./${SETUP_SCRIPT} >& ${SETUP_OUTPUT} || return 2
    fi
    print "running:   $( basename ${TCL_FILE} )"
    ${RUN_TEST} ${TCL_FILE} ${TURBINE_OUTPUT} ${ARGS}
    CODE=${?}
    if (( CODE != 0 ))
    then
      (( REPORT_ERRORS )) && cat ${TURBINE_OUTPUT}
      return 1
    fi

    if [ ! -z "${TURBINE_XPT_FILE}" ]
    then
      print "rerunning with checkpoint: ${TURBINE_XPT_FILE}"
      export TURBINE_XPT_RELOAD="${TURBINE_XPT_FILE}"
      unset TURBINE_XPT_FILE
      ${RUN_TEST} ${TCL_FILE} ${TURBINE_XPT_RELOAD_OUTPUT} ${ARGS} || return 1
      # Test reload from checkpoint file
    fi
  )
  EXIT_CODE=${?}
  popd

  if [ $EXIT_CODE = 2 ]
  then
    echo "Setup script failed"
    return 1
  fi

  if grep -F -q "THIS-TEST-SHOULD-NOT-RUN" ${SWIFT_FILE}
  then
    if [ $EXIT_CODE = 0 ]
    then
      echo "Should have failed at runtime, but succeeded"
    fi
    # This test was intended to fail at run time
    EXIT_CODE=$(( ! EXIT_CODE ))
  else
    # Check for unexecuted transforms
    grep -F -q "WAITING TRANSFORMS" ${TURBINE_OUTPUT}
    # This is 0 if nothing was found
    WAITING_TRANSFORMS=$(( ! ${?} ))
    if (( WAITING_TRANSFORMS ))
      then
      print "Transforms were left in the rule engine!"
      return 1
    fi

    # Check for leaks
    if grep -F -q "LEAK DETECTED:" ${TURBINE_OUTPUT}
    then
      LEAK_TEST_COUNT=$((LEAK_TEST_COUNT + 1))
      if (( LEAK_CHECK ))
        then
        print "Memory leak!"
        return 1
      fi
    fi
  fi
  (( EXIT_CODE )) && return ${EXIT_CODE}

  # Check the test output with the test-specific check script
  if [ -x ${STC_TESTS_DIR}/${CHECK_SCRIPT} ]
  then
    print "executing: $( basename ${CHECK_SCRIPT} )"
    pushd $STC_TESTS_DIR
    ./${CHECK_SCRIPT} >& ${CHECK_OUTPUT} || return 1
    popd
  fi

  # Check the output for expected lines
  if [ -f ${EXP_OUTPUT} ]
  then
    print "checking expected: $( basename ${EXP_OUTPUT} )"
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
  local TEST_PATH=$1
  local OPT_LEVEL=$2
  local EXIT_CODE=$3

  if (( EXIT_CODE == 0 ))
  then
    printf "PASSED\n\n"
  else
    printf "FAILED\n\n"
    if [ ${#STC_OPT_LEVELS} -eq 1 ]
    then
      FAILED_TESTS+=${TEST_PATH}
    else
      FAILED_TESTS+="${TEST_PATH}@O${OPT_LEVEL}"
    fi
    if (( EXIT_ON_FAIL ))
      then
      print "EXIT CODE: ${EXIT_CODE}"
      exit ${EXIT_CODE}
    fi
  fi
}

report_stats_and_exit()
{
  local EXIT_CODE=$1

  print ""
  print -- "--"
  if (( EXIT_CODE != 0 ))
  then
    print "Caught signal: terminating early"
  fi

  print "tests run: ${TESTS_RUN}"
  if [ "${FAILED_TESTS}" != "" ]; then
      print "failed tests: ${#FAILED_TESTS} (${FAILED_TESTS})"
  fi

  if [ ${LEAK_TEST_COUNT} != 0 ]; then
      print "leaky tests: ${LEAK_TEST_COUNT}"
  fi

  if [ "${DISABLED_TESTS}" != "" ]; then
      print "disabled tests: ${#DISABLED_TESTS} (${DISABLED_TESTS})"
  fi
  exit ${EXIT_CODE}
}

TESTS_RUN=0
SWIFT_FILES=( ${STC_TESTS_DIR}/*.swift )
SWIFT_FILE_TOTAL=${#SWIFT_FILES}
FAILED_TESTS=()
LEAK_TEST_COUNT=0
DISABLED_TESTS=()

# Setup signal handler for early termination
trap "report_stats_and_exit 1" SIGHUP SIGINT SIGTERM

for (( i=1 ; i<=SWIFT_FILE_TOTAL ; i++ ))
do
  SWIFT_FILE=${SWIFT_FILES[i]}
  TEST_PATH=${SWIFT_FILE%.swift}
  STC_ARGS="${TEST_PATH}.stcargs"
  TEST_NAME=$( basename ${TEST_PATH} )
  TEST_OUT_PATH="${STC_TESTS_OUT_DIR}/${TEST_NAME}"

  if (( SKIP_COUNT ))
  then
    (( SKIP_COUNT-- ))
    continue
  fi

  if (( MAX_TESTS >= 0 && TESTS_RUN >= MAX_TESTS ))
  then
    break
  fi

  if (( RUN_DISABLED == 1 ))
  then
    :
  elif grep -F -q "SKIP-THIS-TEST" ${SWIFT_FILE}
  then
    DISABLED_TESTS+=${TEST_NAME}
    continue
  fi

  if [[ ${PATTERN} != "" ]]
  then
    if [[ ! ${SWIFT_FILE} =~ ${PATTERN} ]]
      then
      continue
    fi
  fi

  TCL_FILE=${TEST_OUT_PATH}.tcl
  STC_OUT_FILE=${TEST_OUT_PATH}.stc.out
  STC_ERR_FILE=${TEST_OUT_PATH}.stc.err
  STC_LOG_FILE=${TEST_OUT_PATH}.stc.log
  STC_IC_FILE=${TEST_OUT_PATH}.ic

  print "test: ${TESTS_RUN} (${i}/${SWIFT_FILE_TOTAL})"
  for OPT_LEVEL in $STC_OPT_LEVELS
  do
    # Skip specific optimization levels
    if (( RUN_DISABLED == 1 ))
    then
      :
    elif grep -F -q "SKIP-O${OPT_LEVEL}-TEST" ${SWIFT_FILE}
    then
      echo "skip: ${SWIFT_FILE} at O${OPT_LEVEL}"
      DISABLED_TESTS+="${TEST_NAME}@O${OPT_LEVEL}"
      continue
    fi

    compile_test ${OPT_LEVEL}

    COMPILE_CODE=${EXIT_CODE}

    # Default behaviour is to run if it compiles
    RUN_COMPILED_TEST=$(( ! COMPILE_CODE ))

    # Reverse exit code if the case was intended to fail to compile
    if grep -F -q "THIS-TEST-SHOULD-NOT-COMPILE" ${SWIFT_FILE}
    then
      RUN_COMPILED_TEST=0 # Never run these tests
      if grep -F -q "STC INTERNAL ERROR" ${STC_ERR_FILE}
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

    if (( RUN_COMPILED_TEST ))
    then
      if (( COMPILE_ONLY ))
      then
          EXIT_CODE=0
      elif grep -F -q "COMPILE-ONLY-TEST" ${SWIFT_FILE}
      then
          EXIT_CODE=0
      else
          run_test
          EXIT_CODE=${?}
      fi
    fi

    if grep -F -q "THIS-TEST-SHOULD-CAUSE-WARNING" ${SWIFT_FILE}
    then
      if grep -q "^WARN" ${STC_ERR_FILE}
      then
          :
      else
          EXIT_CODE=1
          printf "No warning in stc output\n"
      fi
    fi
    report_result ${TEST_NAME} ${OPT_LEVEL} ${EXIT_CODE}
  done
  (( TESTS_RUN++ ))
done

report_stats_and_exit 0
