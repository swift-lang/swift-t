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

THIS=$0
SCRIPT=${THIS%.sh}.tcl
OUTPUT=${THIS%.sh}.out

source $( dirname $0 )/setup.sh > ${OUTPUT} 2>&1

PROCS=3
export TURBINE_NOOP_WORKERS=1
export TURBINE_NOOP_BUFFER_COUNT=16
export TURBINE_NOOP_MAX_TASK_SIZE=512
bin/turbine -l -n ${PROCS} ${SCRIPT} >> ${OUTPUT} 2>&1
[[ ${?} == 0 ]] || test_result 1

dummy_exp=100
dummy_count=$(grep -q -c -F "DUMMY TASK rank: 1" ${OUTPUT})
if [ "$dummy_count" -ne $dummy_exp ]
then
  echo "Dummy tasks: expected $dummy_act actual $dummy_count"
  exit 1
fi

noop_exp=100
noop_count=$(grep -q -c -F "Launched task: NOOP TASK rank: 1" ${OUTPUT})
if [ "$noop_count" -ne $noop_exp ]
then
  echo "Noop tasks: expected $noop_act actual $noop_count"
  exit 1
fi

noop_completions=$(
    grep 'NOOP: [0-9]* completed' ${OUTPUT} |
    sed 's/^.* NOOP: \([0-9]*\) completed.*$/\1/' |
    awk '{ sum += $1 } END { print sum }')

if [ $noop_completions -ne $noop_exp ]
then
  echo "Noop completions: expected $noop_exp actual $noop_completions"
  exit 1
fi

noop_result_ok=$(grep -c -F 'noop_task_result OK' ${OUTPUT})

if [ $noop_result_ok -ne $noop_exp ]
then
  echo "Noop result ok: expected $noop_exp but got $noop_result_ok"
  exit 1
fi

grep -q "WAITING WORK" ${OUTPUT} && test_result 1

test_result 0
