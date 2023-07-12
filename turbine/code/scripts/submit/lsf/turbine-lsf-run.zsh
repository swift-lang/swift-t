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

# TURBINE LSF RUN

# See run-init.zsh for usage

print "TURBINE-LSF SCRIPT"

export TURBINE_HOME=$( cd $( dirname $0 )/../../.. ; /bin/pwd )

source ${TURBINE_HOME}/scripts/submit/run-init.zsh
if [[ ${?} != 0 ]]
then
  print "Broken Turbine installation!"
  declare TURBINE_HOME
  return 1
fi

# Convert HH:MM:SS to HH:MM (bsub will not accept HH:MM:SS)
if (( ${#WALLTIME} == 8 ))
then
  WALLTIME=${WALLTIME[1,5]}
fi

TURBINE_LSF_M4=${TURBINE_HOME}/scripts/submit/lsf/turbine-lsf.sh.m4
TURBINE_LSF=${TURBINE_OUTPUT}/turbine-lsf.sh

# Filter/create the LSF submit file
m4 -P ${COMMON_M4} ${TURBINE_LSF_M4} > ${TURBINE_LSF}
print "wrote: ${TURBINE_LSF}"

BSUB=bsub

cd ${TURBINE_OUTPUT:A} # Canonicalize
echo "PWD: ${PWD}"

if (( DRY_RUN ))
then
  print "turbine: dry run: submit with 'bsub ${PWD}/turbine-lsf.sh'"
  return 0
fi

# Submit it!
${BSUB} ${TURBINE_LSF} | read MESSAGE
echo $MESSAGE
# Pull out 2nd word without characters '<' and '>'
JOB_ID=${${(z)MESSAGE}[3]}

[[ ${JOB_ID} != "" ]] || abort "bsub failed!"

declare JOB_ID

# Fill in turbine.log
turbine_log >> ${LOG_FILE}
# Fill in jobid.txt
print ${JOB_ID} > ${JOB_ID_FILE}

return 0
