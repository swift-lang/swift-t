#!/bin/zsh
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

# SETUP-TURBINE-SLURM
# Filters the Turbine SLURM template turbine-slurm.sh.m4
# This is done by simply using environment variables
# in M4 to create the Turbine SLURM submit file (turbine-slurm.sh)

# USAGE
# > VAR1=VALUE1 VAR2=VALUE2 setup-turbine-slurm <output>?
# If output is not specified, uses ./turbine-slurm.sh
# Required variables are:
#    PROGRAM: The Turbine program to run
# Recognized variables are:
#    WALLTIME: The PBS walltime as HH:MM:SS (default 15min)
# Then, run sbatch <options> turbine-slurm.sh

export TURBINE_HOME=$( cd "$( dirname "$0" )/../../.." ; /bin/pwd )

source ${TURBINE_HOME}/scripts/helpers.zsh
if [[ ${?} != 0 ]]
then
  print "Broken Turbine installation!"
  declare TURBINE_HOME
  return 1
fi

checkvars PROGRAM
declare PROGRAM

TURBINE_SLURM_M4=${TURBINE_HOME}/scripts/submit/slurm/turbine-slurm.sh.m4
TURBINE_SLURM=${1:-turbine-slurm.sh}
export WALLTIME=${WALLTIME:-00:15:00}
export PPN=${PPN:-1}

touch ${TURBINE_SLURM}
exitcode "Could not write to: ${TURBINE_SLURM}"

m4 ${TURBINE_SLURM_M4} > ${TURBINE_SLURM}
exitcode "Errors in M4 processing!"

print "wrote: ${TURBINE_SLURM}"
