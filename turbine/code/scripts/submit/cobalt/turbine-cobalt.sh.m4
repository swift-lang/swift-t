changecom(`dnl')#!/bin/bash`'bash_l()
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

# Get the time zone: for time stamps on log messages
export TZ=getenv(TZ)

COMMAND="getenv(COMMAND)"
PPN=getenv(PPN)
PROCS=getenv(PROCS)

TURBINE_HOME=getenv(TURBINE_HOME)
TURBINE_STATIC_EXEC=getenv(TURBINE_STATIC_EXEC)
EXEC_SCRIPT=getenv(EXEC_SCRIPT)

source ${TURBINE_HOME}/scripts/turbine-config.sh
if [[ ${?} != 0 ]]
then
  echo "Could not find Turbine settings!"
  exit 1
fi

MODE=getenv(MODE)
LAUNCHER="getenv(TURBINE_LAUNCHER)"
VALGRIND="getenv(VALGRIND)"

export TURBINE_LOG=getenv(TURBINE_LOG)
export ADLB_PRINT_TIME=getenv(ADLB_PRINT_TIME)

if [[ ${MODE} == "cluster" ]]
then
      NODE_ARG="-f ${COBALT_NODEFILE}"
fi

echo "TURBINE SETTINGS"
echo "JOB_ID:       ${COBALT_JOBID}"
echo "DATE:         $(date)"
echo "TURBINE_HOME: ${TURBINE_HOME}"
echo "COMMAND:      ${COMMAND}"
echo "PROCS:        ${PROCS}"
echo "PPN:          ${PPN}"
echo "TCLLIBPATH:   ${TCLLIBPATH}"
echo "LAUNCHER:     ${LAUNCHER}"
[[ -n ${VALGRIND} ]] && \
echo "VALGRIND:     ${VALGRIND}"
echo

# Export all user environment variables
export getenv(USER_ENV_ARRAY)

# Run Turbine:
${LAUNCHER} -l ${NODE_ARG} -n ${PROCS} -ppn ${PPN} \
            ${TURBINE_INTERPOSER:-} ${COMMAND}
CODE=${?}

echo
echo "Turbine launcher done."
echo "CODE: ${CODE}"
echo "COMPLETE: $(date)"

# Return exit code from launcher (mpiexec)
exit ${CODE}
