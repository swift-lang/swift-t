#!/bin/zsh -efu

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

# usage:
#  turbine-cobalt -n <PROCS> [-e <ENV>]* [-o <OUTPUT>] -t <WALLTIME>
#                 <SCRIPT> [<ARG>]*

# Environment variables that must be set:
# MODE: Either "cluster", "BGP", or "BGQ"
# QUEUE: The queue name to use

# Environment variables that may be set:
# PROJECT: The project name to use (default none)
# TURBINE_OUTPUT_ROOT: Where to put Turbine output-
#          a subdirectory based on the current time
#          will be created, reported, and used
#          (default ~/turbine-output)
# PPN: Processes-per-node: see below: (default 1)

# On Eureka:   usually set PPN=8
# On the BG/P: usually set PPN=4
# On the BG/Q: usually set PPN=16

# Runs job in TURBINE_OUTPUT
# Pipes output and error to TURBINE_OUTPUT/output.txt
# Creates TURBINE_OUTPUT/log.txt and TURBINE_OUTPUT/jobid.txt

# Convention note: This script uses -n <processes>
#                  Cobalt qsub uses -n <nodes>
# (We follow the mpiexec convention.)

export TURBINE_HOME=$( cd $( dirname $0 )/../../.. ; /bin/pwd )

source ${TURBINE_HOME}/scripts/turbine-config.sh

source ${TURBINE_HOME}/scripts/submit/run-init.zsh

# Log file for turbine-cobalt settings
LOG_FILE=${TURBINE_OUTPUT}/turbine.log
# All output from job, including error stream
OUTPUT_FILE=${TURBINE_OUTPUT}/output.txt

print "SCRIPT:            ${SCRIPT}" >> ${LOG_FILE}
SCRIPT_NAME=$( basename ${SCRIPT} )
cp ${SCRIPT} ${TURBINE_OUTPUT}

JOB_ID_FILE=${TURBINE_OUTPUT}/jobid.txt

# Turbine-specific environment (with defaults)
ADLB_SERVERS=${ADLB_SERVERS:-1}
TURBINE_WORKERS=$(( PROCS - ADLB_SERVERS ))
ADLB_EXHAUST_TIME=${ADLB_EXHAUST_TIME:-0.1}
ADLB_PRINT_TIME=${ADLB_PRINT_TIME:-0}
TURBINE_LOG=${TURBINE_LOG:-1}
TURBINE_DEBUG=${TURBINE_DEBUG:-1}
ADLB_DEBUG=${ADLB_DEBUG:-1}
N=${N:-0}

declare SCRIPT_NAME
# declare MODE

if [[ ${MODE} == "cluster" ]]
then
  MODE_ARG=""
elif [[ ${MODE} == "BGP" ]]
  then
  MODE_ARG="--mode vn"
elif [[ ${MODE} == "BGQ" ]]
then
  # On the BG/Q, we need PPN: default 1
  MODE_ARG="--proccount ${PROCS} --mode c${PPN}"
else
  print "Unknown mode: ${MODE}"
  exit 1
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
       N=${N}
     )

if [[ ${MODE} == "BGQ" ]]
then
  env+=( BG_SHAREDMEMSIZE=32MB
         PAMID_VERBOSE=1
       )
fi

# Round NODES up for extra processes
NODES=$(( PROCS/PPN ))
(( PROCS % PPN )) && (( NODES++ )) || true
declare NODES

if [[ ${CHANGE_DIRECTORY} == "" ]]
then
  WORK_DIRECTORY=${TURBINE_OUTPUT}
else
  WORK_DIRECTORY=${CHANGE_DIRECTORY}
fi

# Create the environment list in a format Cobalt can support
ENV_LIST=${env}
export ENV_LIST

if (( ${EXEC_SCRIPT} == 0 )) 
then 
  COMMAND="${TCLSH} ${TURBINE_OUTPUT}/${SCRIPT_NAME} ${ARGS}"
else 
  COMMAND="${TURBINE_OUTPUT}/${SCRIPT_NAME} ${ARGS}"
fi

# Launch it
if [[ ${MODE} == "cluster" ]]
then
  export COMMAND
  m4 ${TURBINE_HOME}/scripts/submit/cobalt/turbine-cobalt.sh.m4 > \
     ${TURBINE_OUTPUT}/turbine-cobalt.sh
  chmod u+x ${TURBINE_OUTPUT}/turbine-cobalt.sh
  qsub -n ${NODES}             \
       -t ${WALLTIME}          \
       -q ${QUEUE}             \
       --cwd ${WORK_DIRECTORY} \
       ${=MODE_ARG}            \
       -o ${TURBINE_OUTPUT}/output.txt \
       -e ${TURBINE_OUTPUT}/output.txt \
       --jobname "Swift" \
       ${TURBINE_OUTPUT}/turbine-cobalt.sh | \
    read JOB_ID
else # Blue Gene
  qsub -n ${NODES}             \
       -t ${WALLTIME}          \
       -q ${QUEUE}             \
       --cwd ${WORK_DIRECTORY} \
       --env "${ENV}"          \
       ${=MODE_ARG}            \
       -o ${TURBINE_OUTPUT}/output.txt \
       -e ${TURBINE_OUTPUT}/output.txt \
        ${TCLSH} ${TURBINE_OUTPUT}/${SCRIPT_NAME} ${=ARGS} | \
    read JOB_ID
fi

if [[ ${JOB_ID} == "" ]]
then
  print "cqsub failed!"
  exit 1
fi

declare JOB_ID

# Fill in log
{
  print "JOB:               ${JOB_ID}"
  print "COMMAND:           ${SCRIPT_NAME} ${ARGS}"
  print "WORK_DIRECTORY:    ${WORK_DIRECTORY}"
  print "HOSTNAME:          $( hostname -d )"
  print "SUBMITTED:         $( date_nice )"
  print "PROCS:             ${PROCS}"
  print "TURBINE_WORKERS:   ${TURBINE_WORKERS}"
  print "ADLB_SERVERS:      ${ADLB_SERVERS}"
  print "WALLTIME:          ${WALLTIME}"
  print "ADLB_EXHAUST_TIME: ${ADLB_EXHAUST_TIME}"
} >> ${LOG_FILE}

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
