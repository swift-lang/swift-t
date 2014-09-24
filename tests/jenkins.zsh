#!/bin/zsh

# STC TESTS JENKINS.ZSH

# Copyright 2013 University of Chicago and Argonne National Laboratory
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License

# Runs test suite on Jenkins server

check_error()
{
  CODE=$1
  MSG=$2
  if (( CODE != 0 ))
  then
    print "Operation failed: ${MSG}"
    print "Exit code: ${CODE}"
    exit 1
  fi
  return 0
}

pwd

print
printf "DATE: "
date "+%m/%d/%Y %I:%M%p"
print

print
printf "PWD: ${PWD}"
print

print
print "I am: $0"
print

TESTS_SKIP=0
TESTS_TOTAL=-1 # May set to -1 to run all
INSTALL_ROOT=/tmp/exm-install
TURBINE=${INSTALL_ROOT}/turbine
STC=${INSTALL_ROOT}/stc
MPICH=/tmp/mpich-install
export PATH=${MPICH}/bin:${TURBINE}/bin:${STC}/bin:${PATH}
# print -l ${path}
print "Using STC:     $( which stc )"
print "Using Turbine: $( which turbine )"
turbine -v

cd tests

export TURBINE_HOME=${TURBINE}
export ADLB_PERF_COUNTERS=0
source ./run-tests.zsh -O0 -O1 -O2 -O3 \
      -c -k ${TESTS_SKIP} -n ${TESTS_TOTAL} |& \
      tee results.out
check_error ${pipestatus[1]} "run-tests.zsh"

SUITE_RESULT="result_aggregate.xml";
./jenkins-results.zsh > ${SUITE_RESULT}
check_error ${?} "jenkins-results.zsh"

print
print "SUITE RESULT XML:"
cat ${SUITE_RESULT}
