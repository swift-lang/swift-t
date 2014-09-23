#!/bin/bash
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

# TURBINE-SLURM.SH

# Defaults
L=""
NODES=0
PROGRAM=""
WALLTIME="0:05:00"

# This line produces a false autoscan message on token "ln"
while getopts "ln:t:" OPT
do
  case ${OPT} in
    l)
      L="-l"
      shift
      ;;
    n)
      NODES=${OPTARG}
      echo before $*
      shift 2
      echo after $*
      ;;
    t)
      WALLTIME=${OPTARG}
      shift 2
      ;;
  esac
done

PROGRAM=$1

if [[ ${PROGRAM} == "" ]]
then
  echo "No program!"
  exit 1
fi

if [[ ${NODES} == 0 ]]
then
  echo "Cannot run with 0 processes!"
  exit 1
fi

#SBATCH --time=${WALLTIME}
#SBATCH --nodes=${NODES}

export TURBINE_HOME=$( cd "$( dirname "$0" )/../../.." ; /bin/pwd )

echo "TURBINE_HOME: ${TURBINE_HOME}"
echo "PROGRAM:      ${PROGRAM}"
echo "NODES:        ${NODES}"
echo "WALLTIME:     ${WALLTIME}"

source ${TURBINE_HOME}/scripts/turbine-config.sh
if [[ ${?} != 0 ]]
then
  echo "Could not find Turbine settings!"
  exit 1
fi

${TURBINE_LAUNCH} ${L} -n ${NODES} ${TCLSH} ${PROGRAM}
# Return exit code from slurm
