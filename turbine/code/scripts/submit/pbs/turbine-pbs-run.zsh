#!/bin/zsh -ef

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
#  turbine-pbs-run.zsh -n <PROCS> [-e <ENV>]* [-o <OUTPUT>] -t <WALLTIME>
#                      <SCRIPT> [<ARG>]*

# Environment variables that must be set:
# QUEUE: The queue name to use

# Environment variables that may be set:
# PROJECT: The project name to use (default none)
# TURBINE_OUTPUT_ROOT: Where to put Turbine output-
#          a subdirectory based on the current time
#          will be created, reported, and used
#          (default ~/turbine-output)
# PPN: Processes-per-node: see below

# Runs job in TURBINE_OUTPUT
# Pipes output and error to TURBINE_OUTPUT/output.txt
# Creates TURBINE_OUTPUT/log.txt and TURBINE_OUTPUT/jobid.txt

# Convention note: This script uses -n <processes>
# (We follow the mpiexec convention.)

export TURBINE_HOME=$( cd $( dirname $0 )/../../.. ; /bin/pwd )
# declare TURBINE_HOME

source ${TURBINE_HOME}/scripts/submit/run-init.zsh

# Log file for turbine-cobalt settings
LOG_FILE=${TURBINE_OUTPUT}/turbine-pbs.log

print "SCRIPT:            ${SCRIPT}" >> ${LOG_FILE}
SCRIPT_NAME=$( basename ${SCRIPT} )
cp ${SCRIPT} ${TURBINE_OUTPUT}
export PROGRAM=${TURBINE_OUTPUT}/${SCRIPT_NAME}

JOB_ID_FILE=${TURBINE_OUTPUT}/jobid.txt

# Turbine-specific environment (with defaults)
export TURBINE_JOBNAME=${TURBINE_JOBNAME:-SWIFT}
export ADLB_SERVERS=${ADLB_SERVERS:-1}
export TURBINE_WORKERS=$(( PROCS - ADLB_SERVERS ))
export ADLB_EXHAUST_TIME=${ADLB_EXHAUST_TIME:-1}
export TURBINE_LOG=${TURBINE_LOG:-1}
export TURBINE_DEBUG=${TURBINE_DEBUG:-1}
export ADLB_DEBUG=${ADLB_DEBUG:-1}
export PPN=${PPN:-4}
export N=${N:-0}

# We use PBS -V to export all environment variables to the job
# Evaluate any user turbine-pbs-run -e K=V settings here:
for kv in ${env}
do
  eval export ${kv}
done

declare SCRIPT_NAME

declare PROCS PPN

# Round NODES up for extra processes
export NODES=$(( PROCS/PPN ))
(( PROCS % PPN )) && (( NODES++ )) || true
declare NODES

TURBINE_PBS_M4=${TURBINE_HOME}/scripts/submit/pbs/turbine.pbs.m4
TURBINE_PBS=${TURBINE_OUTPUT}/turbine.pbs

touch ${TURBINE_PBS}

# Filter/create the PBS submit file
m4 ${TURBINE_PBS_M4} > ${TURBINE_PBS}
print "wrote: ${TURBINE_PBS}"

# Launch it!
qsub ${TURBINE_PBS} | read JOB_ID

[[ ${JOB_ID} != "" ]] || abort "qsub failed!"

declare JOB_ID

# Fill in log.txt
{
  print "JOB:               ${JOB_ID}"
  print "COMMAND:           ${SCRIPT_NAME} ${ARGS}"
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

return 0
