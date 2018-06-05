#!/bin/bash -l
ifelse(getenv_nospace(PROJECT), `',,#COBALT -A getenv_nospace(PROJECT)
)ifelse(getenv_nospace(QUEUE), `',,#COBALT -q getenv(QUEUE)
)#COBALT -n getenv(NODES)
#COBALT -t getenv(WALLTIME)
#COBALT --cwd getenv(WORK_DIRECTORY)
#COBALT -o getenv_nospace(TURBINE_OUTPUT)/output.txt
#COBALT -e getenv_nospace(TURBINE_OUTPUT)/output.txt
#COBALT --jobname getenv(TURBINE_JOBNAME)
ifelse(getenv_nospace(MAIL_ARG), `',,#COBALT 'getenv(MAIL_ARG)'
)

# These COBALT directives have to stay right at the top of the file!
# No blank lines are allowed, making this look cluttered.

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

# TURBINE-THETA.SH

# Created: esyscmd(`date')

source /opt/modules/default/init/bash
module load modules
PATH=/opt/cray/elogin/eproxy/2.0.14-4.3/bin:$PATH # For aprun
module swap PrgEnv-intel/6.0.4 PrgEnv-gnu
module load alps

set -eu

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

LAUNCHER="getenv(TURBINE_LAUNCHER)"
VALGRIND="getenv(VALGRIND)"

export TURBINE_LOG=getenv(TURBINE_LOG)
export ADLB_PRINT_TIME=getenv(ADLB_PRINT_TIME)

echo "TURBINE SETTINGS"
echo "JOB_ID:  ${COBALT_JOBID}"
echo "DATE:    $(date)"
echo "TURBINE_HOME: ${TURBINE_HOME}"
echo "PROCS:   ${PROCS}"
echo "PPN:${PPN}"
# echo "TCLLIBPATH:   ${TCLLIBPATH}"
# echo "LAUNCHER:${LAUNCHER}"
#[[ -n ${VALGRIND} ]] && \
# echo "VALGRIND:${VALGRIND}"
echo

# Put environment variables from run-init into 'aprun -e' format
ENV_LIST="getenv(ENV_LIST)"
APRUN_ENVS=""
for KV in ${ENV_LIST}
do
    APRUN_ENVS+="-e ${KV} "
done

TURBINE_LAUNCH_OPTIONS="getenv(TURBINE_LAUNCH_OPTIONS)"

# Run Turbine:
set -x
aprun -n ${PROCS} -N ${PPN} \
      ${TURBINE_LAUNCH_OPTIONS:-} \
      ${APRUN_ENVS} \
      ${VALGRIND} \
      ${COMMAND}
CODE=${?}

echo
echo "Turbine Theta launcher done."
echo "CODE: ${CODE}"
echo "COMPLETE: $(date)"

# Return exit code from launcher (aprun)
exit ${CODE}

# Local Variables:
# mode: m4
# End:
