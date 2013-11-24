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

set -x

THIS=$0
SCRIPT=${THIS%.sh}.tcl
OUTPUT=${THIS%.sh}.out

# Shouldn't leak any memory
export ADLB_LEAK_CHECK=true

# Force reallocation code paths
export ADLB_DEBUG_SYNC_BUFFER_SIZE=4

# Test fails with deadlock - time limit it
TIME_LIMIT=10

bin/turbine -l -n 3 ${SCRIPT} &> ${OUTPUT} &
pid=$!
for i in `seq $TIME_LIMIT`; do
  sleep 1
  if ps -p $pid &> /dev/null ; then
    :
  else
    break
  fi
done

if ps -p $pid &> /dev/null ; then
  echo "${TIME_LIMIT}s time limit expired"
  exit 1  
fi

wait $pid
RC=${?}
[[ ${RC} == 0 ]] || exit 1

grep -q "LEAK" ${OUTPUT} && exit 1

exit 0
