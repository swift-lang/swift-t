#!/bin/zsh -f

# STC RUN-TESTS

# See About.txt for notes

# Defaults
EXIT_ON_FAIL=1
MAX_TESTS=-1 # by default, unlimited
PATTERN=""
SKIP_COUNT=0
VERBOSE=0
STC_OPT_LEVELS=() #-O levels to test for STC
DEFAULT_STC_OPT_LEVEL=1
ADDTL_STC_ARGS=()
LEAK_CHECK=0
STC_TESTS_OUT_DIR=

# Speed up the tests
if [ -z ${ADLB_EXHAUST_TIME} ]; then
    export ADLB_EXHAUST_TIME=1
fi

while getopts "ck:n:p:VO:t:T:alo:" OPTION
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
      LEAK_CHECK=1
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

  ${STC} -l ${STC_LOG_FILE} -O ${STC_OPT_LEVEL} -C ${STC_IC_FILE} \
            ${ADDTL_STC_ARGS} \
            ${SWIFT_FILE} ${TCL_FILE} \
            > ${STC_OUT_FILE} 2> ${STC_ERR_FILE}
  EXIT_CODE=${?}
}

run_test()
# Run test under Turbine/MPI
{
  SETUP_SCRIPT=${TEST_PATH}.setup.sh
  CHECK_SCRIPT=${TEST_PATH}.check.sh

  SETUP_OUTPUT=${SETUP_SCRIPT%.sh}.out
  CHECK_OUTPUT=${CHECK_SCRIPT%.sh}.out
  EXP_OUTPUT=${TEST_PATH}.exp
  TURBINE_OUTPUT=${TCL_FILE%.tcl}.out
  export TURBINE_OUTPUT

  ARGS=""
  ARGS_FILE=${TEST_PATH}.args

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
  print "running:   $( basename ${TCL_FILE} )"
  ${RUN_TEST} ${TCL_FILE} ${TURBINE_OUTPUT} ${ARGS}
  EXIT_CODE=${?}

  if grep -q "THIS-TEST-SHOULD-NOT-RUN" ${SWIFT_FILE}
  then
    # This test was intended to fail at run time
    EXIT_CODE=$(( ! EXIT_CODE ))
  else
    # Check for unexecuted transforms
    grep -q "WAITING TRANSFORMS" ${TURBINE_OUTPUT}
    # This is 0 if nothing was found
    WAITING_TRANSFORMS=$(( ! ${?} ))
    if (( WAITING_TRANSFORMS ))
      then
      print "Transforms were left in the rule engine!"
      return 1
    fi

    if (( LEAK_CHECK ))
    then
      # Check for leaks
      grep -q "ADLB: LEAK:" ${TURBINE_OUTPUT}
      # This is 0 if nothing was found
      LEAKS=$(( ! ${?} ))
      if (( LEAKS ))
        then
        print "Memory leak!"
        return 1
      fi
    fi
  fi
  (( EXIT_CODE )) && return ${EXIT_CODE}

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

TEST_COUNT=0
SWIFT_FILES=( ${STC_TESTS_DIR}/*.swift )
SWIFT_FILE_TOTAL=${#SWIFT_FILES}
FAILED_TESTS=()
for (( i=1 ; i<=SWIFT_FILE_TOTAL ; i++ ))
do
  SWIFT_FILE=${SWIFT_FILES[i]}
  TEST_PATH=${SWIFT_FILE%.swift}
  TEST_NAME=$( basename ${TEST_PATH} )
  TEST_OUT_PATH="${STC_TESTS_OUT_DIR}/${TEST_NAME}"

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

  if [[ ${PATTERN} != "" ]]
  then
    if [[ ! ${SWIFT_FILE} =~ ${PATTERN} ]]
      then
      continue
    fi
  fi

  (( TEST_COUNT++ ))
  TCL_FILE=${TEST_OUT_PATH}.tcl
  STC_OUT_FILE=${TEST_OUT_PATH}.stc.out
  STC_ERR_FILE=${TEST_OUT_PATH}.stc.err
  STC_LOG_FILE=${TEST_OUT_PATH}.stc.log
  STC_IC_FILE=${TEST_OUT_PATH}.ic

  print "test: ${TEST_COUNT} (${i}/${SWIFT_FILE_TOTAL})"
  for OPT_LEVEL in $STC_OPT_LEVELS
  do
    compile_test ${OPT_LEVEL}

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
      if grep -q "COMPILE-ONLY-TEST" ${SWIFT_FILE}
      then
          EXIT_CODE=0
      else
          run_test
          EXIT_CODE=${?}
      fi
    fi

    if grep -q "THIS-TEST-SHOULD-CAUSE-WARNING" ${SWIFT_FILE}
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
done

print -- "--"
print "tests: ${TEST_COUNT}"
if [ "${FAILED_TESTS}" != "" ]; then
    print "failed tests: ${#FAILED_TESTS} (${FAILED_TESTS})"
fi
exit 0
