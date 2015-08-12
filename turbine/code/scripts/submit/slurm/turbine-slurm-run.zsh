#!/bin/zsh
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

# TURBINE SLURM RUN
# Creates a SLURM run file and runs it on the given program

print "TURBINE-SLURM SCRIPT"

export TURBINE_HOME=$( cd "$(dirname "$0")/../../.." ; /bin/pwd )
source ${TURBINE_HOME}/scripts/submit/run-init.zsh
if [[ ${?} != 0 ]]
then
  print "Broken Turbine installation!"
  declare TURBINE_HOME
  return 1
fi
declare TURBINE_HOME

checkvars PROGRAM NODES PPN
export    PROGRAM NODES PPN

TURBINE_SLURM_M4=${TURBINE_HOME}/scripts/submit/slurm/turbine-slurm.sh.m4
TURBINE_SLURM=${TURBINE_OUTPUT}/turbine-slurm.sh

m4 ${TURBINE_SLURM_M4} > ${TURBINE_SLURM}

print "wrote: ${TURBINE_SLURM}"

QUEUE_ARG=""
if (( ${+QUEUE} ))
then
  QUEUE_ARG="--partition=${QUEUE}"
fi

ACCOUNT_ARG=""
if (( ${+PROJECT} ))
then
  ACCOUNT_ARG="--account=${PROJECT}"
fi

# SLURM exports all environment variables to the job by default
# Evaluate any user turbine-slurm-run -e K=V settings here:
for kv in ${env}
do
  eval export ${kv}
done

sbatch --exclusive --constraint=ib \
  --output=${OUTPUT_FILE}          \
  --error=${OUTPUT_FILE}           \
  ${QUEUE_ARG} ${ACCOUNT_ARG}      \
  --job-name=${TURBINE_JOBNAME}    \
  ${TURBINE_SLURM} ${PROGRAM} ${ARGS} | read __ __ __ JOB_ID

# JOB_ID must be an integer:
if [[ ${JOB_ID} == "" || ${JOB_ID} != <-> ]]
then
  abort "sbatch failed!"
fi
declare JOB_ID

# Fill in turbine.log
turbine_log >> ${LOG_FILE}
# Fill in jobid.txt
print ${JOB_ID} > ${JOB_ID_FILE}

return 0
