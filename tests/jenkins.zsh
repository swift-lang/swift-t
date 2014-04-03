#!/bin/zsh

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
}

set -ux

pwd
TESTS_SKIP=0
TURBINE=/tmp/exm-install/turbine
STC=/tmp/exm-install/stc
MPICH=/tmp/mpich-install
export PATH=${MPICH}/bin:${TURBINE}/bin:${STC}/bin:${PATH}
# print -l ${path}
which stc
which turbine
turbine -v

ant -Ddist.dir=${STC} -Dturbine.home=${TURBINE} install
check_error ${?} "ant"

cd tests

./run-tests.zsh -c -k $TESTS_SKIP |& tee results.out
check_error ${pipestatus[1]} "run-tests.zsh"

./jenkins-results.zsh
check_error ${?} "jenkins-results.zsh"
