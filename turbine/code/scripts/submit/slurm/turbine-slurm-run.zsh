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

# TURBINE-SLURM-RUN
# Creates a SLURM run file and runs it on the given program

# USAGE
# > VAR1=VALUE1 VAR2=VALUE2 turbine-slurm-run.zsh <PROGRAM> <ARGS>*

# ENVIRONMENT
# TODO: User input should be PROCS, not NODES (#648)
# NODES: Number of nodes to use
# PPN:   Processes-per-node

PROGRAM=$1

export TURBINE_HOME=$( cd "$(dirname "$0")/../../.." ; /bin/pwd )
declare TURBINE_HOME
source ${TURBINE_HOME}/scripts/helpers.zsh
if [[ ${?} != 0 ]]
then
  print "Broken Turbine installation!"
  declare TURBINE_HOME
  return 1
fi

checkvars PROGRAM NODES PPN
declare   PROGRAM NODES PPN
export    PROGRAM NODES PPN

setup-turbine-slurm.zsh
exitcode "setup-turbine-slurm failed!"

sbatch --exclusive --constraint=ib ./turbine-slurm.sh ${PROGRAM}
