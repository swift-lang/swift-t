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

JOB_ID_FILE=${TURBINE_OUTPUT}/jobid.txt

# We use PBS -V to export all environment variables to the job
# Evaluate any user turbine-pbs-run -e K=V settings here:
for kv in ${env}
do
  eval export ${kv}
done

TURBINE_PBS_M4=${TURBINE_HOME}/scripts/submit/pbs/turbine.pbs.m4
TURBINE_PBS=${TURBINE_OUTPUT}/turbine.pbs

# Filter/create the PBS submit file
m4 ${TURBINE_PBS_M4} > ${TURBINE_PBS}
print "wrote: ${TURBINE_PBS}"

# Launch it!
qsub ${TURBINE_PBS} | read JOB_ID

[[ ${JOB_ID} != "" ]] || abort "qsub failed!"

declare JOB_ID

# Fill in log.txt
turbine_log >> ${LOG_FILE}
# Fill in jobid.txt
print ${JOB_ID} > ${JOB_ID_FILE}

return 0
