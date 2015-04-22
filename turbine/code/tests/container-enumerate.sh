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

source tests/test-helpers.sh

# Test CONTAINER-ENUMERATE.TCL
# WARNING: This test requires GNU sed

THIS=$0
SCRIPT=${THIS%.sh}.tcl
OUTPUT=${THIS%.sh}.out

source $( dirname $0 )/setup.sh > ${OUTPUT} 2>&1

set -x

if ! sed --version | grep GNU > /dev/null
then
  echo "need GNU sed: skipping this test..."
  test_result 0
fi

bin/turbine -l -n ${PROCS} ${SCRIPT} >> ${OUTPUT} 2>&1
[[ ${?} == 0 ]] || test_result 1

# Read member TDs to search for them later
M1=$( sed -n '/.*member1:.*/{s/.*member1: \(.*\)/\1/;p}' ${OUTPUT} )
M2=$( sed -n '/.*member2:.*/{s/.*member2: \(.*\)/\1/;p}' ${OUTPUT} )

grep -q "subscripts: 0 1"       ${OUTPUT} || test_result 1
grep -q "members: ${M1} ${M2}"  ${OUTPUT} || test_result 1
grep -q "dict: 0 ${M1} 1 ${M2}" ${OUTPUT} || test_result 1
grep -q "count: 2"              ${OUTPUT} || test_result 1

test_result 0
