#!/usr/bin/env zsh

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

print "TURBINE-PSIJ SCRIPT"

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

# SLURM exports all environment variables to the job by default
# Evaluate any user turbine-slurm-run -e K=V settings here:
for kv in ${USER_ENV_CODE}
do
  eval export ${kv}
done

# The new script:
turbine-psij.py $TCLSH $PROGRAM

# wait for job completion?
