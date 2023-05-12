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

# TURBINE SGE RUN

# See run-init.zsh for usage

print "TURBINE-SGE SCRIPT"

export TURBINE_HOME=$( cd $( dirname $0 )/../../.. ; /bin/pwd )

source ${TURBINE_HOME}/scripts/submit/run-init.zsh
if [[ ${?} != 0 ]]
then
  print "Broken Turbine installation!"
  declare TURBINE_HOME
  return 1
fi

if (( ! ${+TURBINE_SGE_PE} ))
then
  print "You must set environment variable TURBINE_SGE_PE"
  print "to the desired parallel environment!"
  return 1
fi

# We use SGE -V to export all environment variables to the job
# Evaluate any user turbine-sge-run -e K=V settings here:
for kv in ${env}
do
  eval export ${kv}
done

TURBINE_SGE_M4=${TURBINE_HOME}/scripts/submit/sge/turbine.sge.m4
TURBINE_SGE=${TURBINE_OUTPUT}/turbine.sge

# Filter/create the SGE submit file
m4 -P ${TURBINE_SGE_M4} > ${TURBINE_SGE}
print "wrote: ${TURBINE_SGE}"

# Launch it!
qsub ${TURBINE_SGE} | read JOB_ID

[[ ${JOB_ID} != "" ]] || abort "qsub failed!"

declare JOB_ID

# Fill in log.txt
turbine_log >> ${LOG_FILE}
# Fill in jobid.txt
print ${JOB_ID} > ${JOB_ID_FILE}

return 0
