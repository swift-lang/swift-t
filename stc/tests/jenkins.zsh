#!/bin/zsh
set -eu

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

# Any arguments to this script are passed to run-tests.zsh

print "JENKINS.ZSH"
print " in $(/bin/pwd)"
printf "DATE: $(date "+%m/%d/%Y %I:%M%p")"
print

TESTS_SKIP=0
TESTS_TOTAL=-1 # May set to -1 to run all
INSTALL_ROOT=/tmp/exm-install
TURBINE_INSTALL=${INSTALL_ROOT}/turbine
STC_INSTALL=${INSTALL_ROOT}/stc
MPICH_INSTALL=/tmp/mpich-install

# MPI
export PATH=${MPICH_INSTALL}/bin:${PATH}

# Specify STC and Turbine for run-tests.zsh
export STC=${STC_INSTALL}/bin/stc
export TURBINE_HOME=${TURBINE_INSTALL}

# print -l ${path}

print "Using STC:         ${STC}"
print "Using TURBINE:     ${TURBINE_INSTALL}"
print "Using MPI install: ${MPICH_INSTALL}"
${TURBINE_HOME}/bin/turbine -v

print "stc -v"
${STC} -v
print

set -x

pwd
ls
touch results.out

export ADLB_PERF_COUNTERS=0
nice ./run-tests.zsh -O0 -O1 -O2 -O3 \
      -c -k ${TESTS_SKIP} -n ${TESTS_TOTAL} ${*} |& \
      tee results.out
print

print "Aggregating results..."
SUITE_RESULT="result_aggregate.xml";
./jenkins-results.zsh > ${SUITE_RESULT}

print
print "SUITE RESULT XML:"
cat ${SUITE_RESULT}
