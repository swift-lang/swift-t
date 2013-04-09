changecom(`dnl')#!/bin/bash
# We use changecom to change the M4 comment to dnl, not hash

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

# TURBINE-COBALT.SH

# Created: esyscmd(`date')

COMMAND=esyscmd(`printf $COMMAND')
PPN=esyscmd(`printf $PPN')
PROCS=esyscmd(`printf $PROCS')

TURBINE=$( which turbine )
if [[ ${?} != 0 ]]
then
  echo "Could not find Turbine in PATH!"
  exit 1
fi

export TURBINE_HOME=$( cd $(dirname ${TURBINE})/.. ; /bin/pwd )

source ${TURBINE_HOME}/scripts/turbine-config.sh
if [[ ${?} != 0 ]]
then
  echo "Could not find Turbine settings!"
  exit 1
fi

echo "DATE:         $(date)"
echo "TURBINE_HOME: ${TURBINE_HOME}"
echo "COMMAND:      ${COMMAND}"
echo "PROCS:        ${PROCS}"
echo "PPN:          ${PPN}"
echo "TCLLIBPATH:   ${TCLLIBPATH}"

# Hack for Eureka
MPI=${HOME}/sfw/mpich-3.0.3-x86_64-mx

${MPI}/bin/mpiexec -n ${PROCS} -ppn ${PPN} ${TCLSH} ${COMMAND}
# Return exit code from mpirun
