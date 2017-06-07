#!/bin/zsh -f
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
#                  Cobalt qsub uses -n <nodes>
# (We follow the mpiexec convention.)

print "TURBINE-THETA SCRIPT"

export TURBINE_HOME=$( cd $( dirname $0 )/../../.. ; /bin/pwd )
if [[ ${?} != 0 ]]
then
  print "Broken Turbine installation!"
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
       TURBINE_LOG=${TURBINE_LOG}
       TURBINE_DEBUG=${TURBINE_DEBUG}
       ADLB_DEBUG=${ADLB_DEBUG}
       MPIRUN_LABEL=1
       TURBINE_CACHE_SIZE=0
     )

if [[ ${CHANGE_DIRECTORY} == "" ]]
then
  WORK_DIRECTORY=${TURBINE_OUTPUT}
else
  WORK_DIRECTORY=${CHANGE_DIRECTORY}
fi

MAIL_ARG=""
if (( MAIL_ENABLED ))
then
  MAIL_ARG=( -M ${MAIL_ADDRESS} )
fi

# Create the environment list in a format Cobalt can support
ENV_LIST=${env}
export ENV_LIST

# Launch it
export COMMAND
TURBINE_THETA_M4=${TURBINE_HOME}/scripts/submit/theta/turbine-theta.sh.m4
TURBINE_THETA=${TURBINE_OUTPUT}/turbine-theta.sh
m4 ${TURBINE_THETA_M4} > ${TURBINE_THETA}
print "wrote: ${TURBINE_THETA}"
chmod u+x ${TURBINE_OUTPUT}/turbine-theta.sh
qsub -n ${NODES}                          \
     -t ${WALLTIME}                       \
     ${QUEUE_ARG}                         \
     --cwd ${WORK_DIRECTORY}              \
     ${MAIL_ARG}                          \
     -o ${TURBINE_OUTPUT}/output.txt      \
     -e ${TURBINE_OUTPUT}/output.txt      \
     --jobname ${TURBINE_JOBNAME}         \
     ${TURBINE_OUTPUT}/turbine-theta.sh | \
  read JOB_ID

if [[ ${JOB_ID} == "" ]]
then
  abort "qsub failed!"
fi

declare JOB_ID

# Fill in log
turbine_log >> ${LOG_FILE}

# Fill in jobid.txt
print ${JOB_ID} > ${JOB_ID_FILE}

# Wait for job completion
cqwait ${JOB_ID}

STOP=$( date +%s )
TOTAL_TIME=$( tformat $(( STOP-START )) )
declare TOTAL_TIME

print "COMPLETE:          $( date_nice )" >> ${LOG_FILE}
print "TOTAL_TIME:        ${TOTAL_TIME}"  >> ${LOG_FILE}

# Check for errors in output file
[[ -f ${OUTPUT_FILE} ]] || abort "no output file!"

# This does not work in MODE=cluster (Tukey)
# Report non-zero job result codes
# if ! grep "code: 0" ${OUTPUT_FILE}
# then
#   print "JOB CRASHED" | tee -a ${LOG_FILE}
#   grep "job result code:" ${OUTPUT_FILE} >> ${LOG_FILE}
#   exit 1
# fi

exit 0
