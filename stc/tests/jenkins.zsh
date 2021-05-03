#!/usr/bin/env zsh
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

print "JENKINS ZSH"
print "DATE: $( date "+%Y-%m-%d %H:%M" )\n"

THIS=$( readlink --canonicalize $( dirname $0 ) )
print "THIS: ${THIS}"
cd $THIS
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
print

if (( ${+PARALLEL} ))
then
  print "PARALLEL enabled..."
  # Seed with middle digits from current nanoseconds
  S=$( date +%N )
  RANDOM=${S:1:4}
  export TEST_ADLB_SERVERS=$(( RANDOM %  5 + 1 ))
  print "TEST_ADLB_SERVERS=${TEST_ADLB_SERVERS}"
  export TEST_ADLB_WORKERS=0
  while (( TEST_ADLB_WORKERS < TEST_ADLB_SERVERS ))
  do
    TEST_ADLB_WORKERS=$(( RANDOM % 10 + 10 ))
  done
  print "TEST_ADLB_WORKERS=${TEST_ADLB_WORKERS}"
fi

print "stc -v"
${STC} -v
print

export ADLB_PERF_COUNTERS=0
./run-tests.zsh -O0 -O1 -O2 -O3 \
     -c -k ${TESTS_SKIP} -n ${TESTS_TOTAL} ${*} |& \
     tee results.out
print

print "Aggregating results..."
SUITE_RESULT="result_aggregate.xml";
./jenkins-results.zsh > ${SUITE_RESULT}

print
print "SUITE RESULT XML:"
cat ${SUITE_RESULT}
