#!/bin/zsh -f

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

# Variables that must be set in the environment:
# MODE: Either "cluster", "BGP", or "BGQ"
# QUEUE: The queue name to use

# Variables that may be set in the environment:
# PROJECT: The project name to use
# TURBINE_OUTPUT_ROOT: Where to put Turbine output-
#          a subdirectory will be created, reported, and used
# TURBINE_PPN: Processes-per-node: see below

# On the BG/P: usually set TURBINE_PPN=4  (default 4)
# On the BG/Q: usually set TURBINE_PPN=16 (default 4)
# On Eureka:   usually set TURBINE_PPN=8  (default 4)

# Runs job in TURBINE_OUTPUT
# Pipes output and error to TURBINE_OUTPUT/output.txt
# Creates TURBINE_OUTPUT/log.txt and TURBINE_OUTPUT/jobid.txt

# Convention note: This script uses -n <processes>
#                  Cobalt qsub uses -n <nodes>
# (We follow the mpiexec convention.)

export TURBINE_HOME=$( cd $( dirname $0 )/../../.. ; /bin/pwd )
declare TURBINE_HOME
source ${TURBINE_HOME}/scripts/turbine-config.sh
source ${TURBINE_HOME}/scripts/helpers.zsh

# Defaults:
export PROCS=0
WALLTIME=${WALLTIME:-00:15:00}
TURBINE_OUTPUT_ROOT=${HOME}/turbine-output
VERBOSE=0

# Place to store output directory name
OUTPUT_TOKEN_FILE=turbine-cobalt-directory.txt

# Job environment
typeset -T ENV env
env=()

# Get options
while getopts "d:e:n:o:t:v" OPTION
 do
  case ${OPTION}
   in
   d)
     OUTPUT_TOKEN_FILE=${OPTARG}
     ;;
   e) env+=${OPTARG}
     ;;
   n) PROCS=${OPTARG}
     ;;
   o) TURBINE_OUTPUT_ROOT=${OPTARG}
     ;;
   t) WALLTIME=${OPTARG}
     ;;
   v)
     VERBOSE=1
     ;;
   *)
     print "abort"
     exit 1
     ;;
 esac
done
shift $(( OPTIND-1 ))

if (( VERBOSE ))
then
  set -x
fi

SCRIPT=$1
checkvars QUEUE SCRIPT MODE

shift
ARGS=${*}

START=$( date +%s )

[[ ${PROCS} != 0 ]]
exitcode "PROCS==0"

RUN=$( date_path )

# Create the directory in which to run
TURBINE_OUTPUT=${TURBINE_OUTPUT_ROOT}/${RUN}
declare TURBINE_OUTPUT
print ${TURBINE_OUTPUT} > ${OUTPUT_TOKEN_FILE}
mkdir -p ${TURBINE_OUTPUT}
exitcode "mkdir failed: ${TURBINE_OUTPUT}"

# Log file for turbine-cobalt settings
LOG_FILE=${TURBINE_OUTPUT}/turbine-cobalt.log
# All output from job, including error stream
OUTPUT_FILE=${TURBINE_OUTPUT}/output.txt

print "SCRIPT:            ${SCRIPT}" >> ${LOG_FILE}
SCRIPT_NAME=$( basename ${SCRIPT} )
[[ -f ${SCRIPT} ]]
exitcode "script not found: ${SCRIPT}"
cp ${SCRIPT} ${TURBINE_OUTPUT}
exitcode "copy failed: ${SCRIPT} -> ${TURBINE_OUTPUT}"

JOB_ID_FILE=${TURBINE_OUTPUT}/jobid.txt

source ${TURBINE_HOME}/scripts/turbine-config.sh
exitcode "turbine-config.sh failed!"

# Turbine-specific environment (with defaults)
TURBINE_ENGINES=${TURBINE_ENGINES:-1}
ADLB_SERVERS=${ADLB_SERVERS:-1}
TURBINE_WORKERS=$(( PROCS - TURBINE_ENGINES - ADLB_SERVERS ))
ADLB_EXHAUST_TIME=${ADLB_EXHAUST_TIME:-5}
TURBINE_LOG=${TURBINE_LOG:-1}
TURBINE_DEBUG=${TURBINE_DEBUG:-1}
ADLB_DEBUG=${ADLB_DEBUG:-1}
TURBINE_PPN=${TURBINE_PPN:-4}
N=${N:-0}

env+=( TCLLIBPATH="${TCLLIBPATH}"
       TURBINE_ENGINES=${TURBINE_ENGINES}
       TURBINE_WORKERS=${TURBINE_WORKERS}
       ADLB_SERVERS=${ADLB_SERVERS}
       ADLB_EXHAUST_TIME=${ADLB_EXHAUST_TIME}
       TURBINE_LOG=${TURBINE_LOG}
       TURBINE_DEBUG=${TURBINE_DEBUG}
       ADLB_DEBUG=${ADLB_DEBUG}
       MPIRUN_LABEL=1
       TURBINE_CACHE_SIZE=0
       N=${N}
     )

declare SCRIPT_NAME
declare MODE

if [[ ${MODE} == "cluster" ]]
then
  MODE_ARG=""
elif [[ ${MODE} == "BGP" ]]
  then
  MODE_ARG="--mode vn"
elif [[ ${MODE} == "BGQ" ]]
then
  # On the BG/Q, we need TURBINE_PPN: default 1
  MODE_ARG="--proccount ${PROCS} --mode c${TURBINE_PPN}"
fi

if [[ ${MODE} == "BGQ" ]]
then
  env+=( BG_SHAREDMEMSIZE=32MB
         PAMID_VERBOSE=1
       )
fi

# Round NODES up for extra processes
NODES=$(( PROCS/TURBINE_PPN ))
(( PROCS % TURBINE_PPN )) && (( NODES++ ))
declare NODES

# Launch it
if [[ ${MODE} == "cluster" ]]
then
  export COMMAND="${SCRIPT_NAME} ${ARGS}"
  export PPN=${TURBINE_PPN}
  m4 < ${TURBINE_HOME}/scripts/submit/cobalt/turbine-cobalt.sh.m4 > \
       ${TURBINE_OUTPUT}/turbine-cobalt.sh
  exitcode
  chmod u+x ${TURBINE_OUTPUT}/turbine-cobalt.sh
  exitcode
  qsub -n ${NODES}             \
       -t ${WALLTIME}          \
       -q ${QUEUE}             \
       --cwd ${TURBINE_OUTPUT} \
       --env "${ENV}"          \
       ${=MODE_ARG}            \
       -o ${TURBINE_OUTPUT}/output.txt \
       -e ${TURBINE_OUTPUT}/output.txt \
       ${TURBINE_OUTPUT}/turbine-cobalt.sh | read JOB_ID
else
  qsub -n ${NODES}             \
       -t ${WALLTIME}          \
       -q ${QUEUE}             \
       --cwd ${TURBINE_OUTPUT} \
       --env "${ENV}"          \
       ${=MODE_ARG}            \
       -o ${TURBINE_OUTPUT}/output.txt \
       -e ${TURBINE_OUTPUT}/output.txt \
        ${TCLSH} ${SCRIPT_NAME} ${ARGS} | read JOB_ID
fi

if [[ ${JOB_ID} == "" ]]
then
  print "cqsub failed!"
  exit 1
fi

declare JOB_ID

# Fill in log.txt
{
  print "JOB:               ${JOB_ID}"
  print "COMMAND:           ${SCRIPT_NAME} ${ARGS}"
  print "HOSTNAME:          $( hostname -d )"
  print "SUBMITTED:         $( date_nice )"
  print "PROCS:             ${PROCS}"
  print "TURBINE_ENGINES:   ${TURBINE_ENGINES}"
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
[[ -f ${OUTPUT_FILE} ]]
exitcode "No job error file: expected: ${OUTPUT_FILE}"

# Report non-zero job result codes
grep "job result code:" ${OUTPUT_FILE} | grep -v "code: 0"
if [[ $pipestatus[2] != 1 ]]
then
  print "JOB CRASHED" | tee -a ${LOG_FILE}
  grep "job result code:" ${OUTPUT_FILE} >> ${LOG_FILE}
  exit 1
fi

exit 0
