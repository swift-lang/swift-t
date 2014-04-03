#!/bin/bash
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


# Handle test results for two modes:
# 1) Normal user-based shell/make operation
# 2) Runs under Jenkins
# usage: test_result <CODE>
#                    CODE==0 is success; else is failure
test_result()
{
  RESULT=$1
  if (( ${RESULT} == 0 ))
  then
    echo "TEST RESULT: OK"
    exit 0
  fi

  # Error condition:
  if [[ -n ${JENKINS_HOME} ]]
  then
    # We are running under Jenkins- we cannot test_result non-zero
    echo "ERROR"
    exit 0
  fi

  # Normal run from user - test failed - stop
  echo "ERROR"
  exit 1
}
