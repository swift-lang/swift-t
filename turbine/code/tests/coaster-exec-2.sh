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
export TURBINE_COASTER_WORKERS=1

COASTER_SVC=coaster-service
SVC_CONF="$(dirname $0)/coaster-exec-local.conf"

#TODO: assumes coaster-service on path
if ! which $COASTER_SVC &> /dev/null ; then
  echo "${COASTER_SVC} not on path"
  exit 1
fi

source "${SVC_CONF}"
"${COASTER_SVC}" -nosec -port ${SERVICE_PORT} &
COASTER_SVC_PID=$!

# Reduce heartbeat interval to catch bugs in heartbeat
export COASTER_HEARTBEAT_INTERVAL=1

export TURBINE_COASTER_CONFIG="jobManager=local,maxParallelTasks=64"
TURBINE_COASTER_CONFIG+=",coasterServiceURL=127.0.0.1:53363"

# Delay to allow service to start up
sleep 0.5

if bin/turbine -l -n ${PROCS} ${SCRIPT} >> ${OUTPUT} 2>&1
then
  TURBINE_RC=0
else
  TURBINE_RC=$?
  echo "TURBINE FAILED: ${TURBINE_RC}"
fi

echo "Killing service ${COASTER_SVC_PID} and immediate children"
for child_pid in $(ps -ef| awk '$3 == '${COASTER_SVC_PID}' { print $2 }')
do
  echo "Killing process $pid"
  kill $child_pid
done

kill ${COASTER_SVC_PID}
wait || true

[[ ${TURBINE_RC} == 0 ]] || test_result 1

grep -q "WAITING WORK" ${OUTPUT} && test_result 1

coaster_exp=100
coaster_count=$(grep -q -c -F "COASTER task output set:" ${OUTPUT})
if [ "$coaster_count" -ne "$coaster_exp" ]
then
  echo "Coaster tasks: expected $coaster_act actual $coaster_count"
  exit 1
fi

test_result 0
