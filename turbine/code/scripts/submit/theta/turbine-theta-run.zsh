#!/usr/bin/env zsh
set -eu

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

# See run-init.zsh for usage

# Convention note: This script uses -n <processes>
#                  APRUN       uses -n <processes>
#                  MPIEXEC     uses -n <processes>
#                  Cobalt qsub uses -n <nodes>
# (We follow the MPIEXEC convention.)

print "TURBINE-THETA SCRIPT"

export TURBINE_HOME=$( cd $( dirname $0 )/../../.. ; /bin/pwd )
if (( ${?} != 0 ))
then
  print "turbine-theta-run: Broken Turbine installation!"
  declare TURBINE_HOME
  return 1
fi
source ${TURBINE_HOME}/scripts/submit/run-init.zsh

QUEUE_ARG=""
if (( ${+QUEUE} ))
then
  QUEUE_ARG=( -q ${QUEUE} )
fi

env+=( TCLLIBPATH="${TCLLIBPATH}"
       TURBINE_WORKERS=${TURBINE_WORKERS}
       ADLB_SERVERS=${ADLB_SERVERS}
       ADLB_EXHAUST_TIME=${ADLB_EXHAUST_TIME}
       ADLB_PRINT_TIME=${ADLB_PRINT_TIME}
       TURBINE_OUTPUT=${TURBINE_OUTPUT}
       TURBINE_LOG=${TURBINE_LOG}
       TURBINE_DEBUG=${TURBINE_DEBUG}
       ADLB_DEBUG=${ADLB_DEBUG}
       MPIRUN_LABEL=1
       TURBINE_CACHE_SIZE=0
     )

if [[ ${CHANGE_DIRECTORY} == "" ]]
then
  export WORK_DIRECTORY=${TURBINE_OUTPUT}
else
  export WORK_DIRECTORY=${CHANGE_DIRECTORY}
fi

if (( MAIL_ENABLED ))
then
  export MAIL_ARG="-M ${MAIL_ADDRESS}"
fi

# Create the environment list in a format Cobalt can support
ENV_LIST=${env}
export ENV_LIST

# Launch it
export COMMAND
TURBINE_THETA_M4=${TURBINE_HOME}/scripts/submit/theta/turbine-theta.sh.m4
TURBINE_THETA=${TURBINE_OUTPUT}/turbine-theta.sh
m4 ${COMMON_M4} ${TURBINE_THETA_M4} > ${TURBINE_THETA}
print "wrote submit script: ${TURBINE_THETA}"
chmod u+x ${TURBINE_OUTPUT}/turbine-theta.sh
print "running qsub ..."
qsub ${TURBINE_OUTPUT}/turbine-theta.sh | read JOB_ID

if [[ ${pipestatus[1]} != 0 ]] || [[ ${JOB_ID} == "" ]]
then
  abort "qsub failed! ${JOB_ID}" # JOB_ID may have an error message
fi

declare JOB_ID

# Fill in log
turbine_log >> ${LOG_FILE}

# Fill in jobid.txt
print ${JOB_ID} > ${JOB_ID_FILE}

if (( ! WAIT_FOR_JOB ))
then
  exit 0
fi

print "turbine-theta-run: waiting for job completion..."

# Wait for job completion
cqwait ${JOB_ID}

print "job complete."

STOP=$( date +%s )
TOTAL_TIME=$( tformat $(( STOP-START )) )
declare TOTAL_TIME

print "COMPLETE:          $( date_nice )" >> ${LOG_FILE}
print "TOTAL_TIME:        ${TOTAL_TIME}"  >> ${LOG_FILE}

# Check for errors in output file
[[ -f ${OUTPUT_FILE} ]] || abort "no output file!"

# This does not work in MODE=cluster (Tukey)
# Report non-zero job result codes
if ! grep "CODE: 0" ${OUTPUT_FILE}
then
  print "JOB CRASHED" | tee -a ${LOG_FILE}
  grep "job result code:" ${OUTPUT_FILE} >> ${LOG_FILE}
  exit 1
fi

exit 0
