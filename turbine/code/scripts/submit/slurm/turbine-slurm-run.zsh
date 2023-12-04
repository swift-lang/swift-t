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

# TURBINE SLURM RUN
# Creates a SLURM run file and runs it on the given program

print "TURBINE-SLURM SCRIPT"

export TURBINE_HOME=$( cd "$(dirname "$0")/../../.." ; /bin/pwd )
source ${TURBINE_HOME}/scripts/submit/run-init.zsh
if (( ${?} != 0 ))
then
  print "Broken Turbine installation!"
  declare TURBINE_HOME
  return 1
fi
declare TURBINE_HOME

checkvars PROGRAM NODES PPN
export    PROGRAM NODES PPN

export TURBINE_LAUNCH_OPTIONS=${TURBINE_LAUNCH_OPTIONS:-}
if (( TURBINE_PREALLOCATION ))
then
  TURBINE_LAUNCH_OPTIONS+="--output=${OUTPUT_FILE} "
  TURBINE_LAUNCH_OPTIONS+="--error=${OUTPUT_FILE} "
  TURBINE_LAUNCH_OPTIONS+="--nodes=${NODES} "
  TURBINE_LAUNCH_OPTIONS+="--ntasks-per-node=${PPN} "
  TURBINE_LAUNCH_OPTIONS+="--chdir=${TURBINE_OUTPUT}"
fi

TURBINE_SLURM_M4=${TURBINE_HOME}/scripts/submit/slurm/turbine-slurm.sh.m4
TURBINE_SLURM=${TURBINE_OUTPUT}/turbine-slurm.sh

m4 -P ${COMMON_M4} ${TURBINE_SLURM_M4} > ${TURBINE_SLURM}
chmod u+x ${TURBINE_SLURM}

print "wrote: ${TURBINE_SLURM}"

# SLURM exports all environment variables to the job by default
# Evaluate any user turbine-slurm-run -e K=V settings here:
for kv in ${USER_ENV_CODE}
do
  eval export ${kv}
done

TURBINE_PREALLOCATION=${TURBINE_PREALLOCATION:-0}
SUBMIT_PROGRAM=sbatch
if (( TURBINE_PREALLOCATION ))
then
  SUBMIT_PROGRAM=
fi
SUBMIT_COMMAND=( ${SUBMIT_PROGRAM} ${TURBINE_SLURM} )

print ${SUBMIT_COMMAND} > ${TURBINE_OUTPUT}/submit.sh
chmod u+x ${TURBINE_OUTPUT}/submit.sh

if (( DRY_RUN ))
then
  print "turbine: dry run: submit with ${TURBINE_OUTPUT}/submit.sh"
  return 0
fi

if (( ! TURBINE_PREALLOCATION ))
then
  # Submit it!
  # Most systems put error messages on stderr
  # Stampede2 produces useful error messages on stdout
  SUBMIT_OUT=$( ${SUBMIT_COMMAND} || true )
  JOB_ID=$( echo ${SUBMIT_OUT} | grep -o "[1-9][0-9]*$" || true )
  # JOB_ID must be an integer:
  if [[ ${JOB_ID} == "" || ${JOB_ID} != <-> ]]
  then
    echo  ${SUBMIT_OUT}
    abort "sbatch failed!"
  fi
else
  if ! ${SUBMIT_COMMAND}
  then
    abort "submit command failed: ${SUBMIT_COMMAND}"
  fi
  JOB_ID=${SLURM_JOB_ID}
fi
declare JOB_ID

# Fill in turbine.log
turbine_log >> ${LOG_FILE}
# Fill in jobid.txt
print ${JOB_ID} > ${JOB_ID_FILE}

return 0
