m4_changecom(`dnl')#!/bin/bash`'bash_l()
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

# TURBINE PBS SH M4

# Turbine PBS template.  This is automatically filled in
# by M4 in turbine-pbs-run.zsh

# Created: m4_esyscmd(`date "+%Y-%m-%d %H:%M:%S"')

#PBS -N getenv(TURBINE_JOBNAME)
m4_ifelse(getenv(TURBINE_POLARIS),`1',
#PBS -l select=getenv(NODES):system=polaris ,
#PBS -l nodes=getenv_nospace(NODES):ppn=getenv(PPN))
#PBS -l walltime=getenv(WALLTIME)
#PBS -j oe
#PBS -o getenv(OUTPUT_FILE)
#PBS -V

m4_ifelse(getenv(PROJECT),`',,
#PBS -A getenv(PROJECT)
)
m4_ifelse(getenv(QUEUE),`',,
#PBS -q getenv(QUEUE)
)

# BEGIN TURBINE_DIRECTIVE
getenv(TURBINE_DIRECTIVE)
# END TURBINE_DIRECTIVE

set -eu

TURBINE_HOME=getenv(TURBINE_HOME)

source ${TURBINE_HOME}/scripts/helpers.sh

VERBOSE=getenv(VERBOSE)
if (( ${VERBOSE} ))
then
 set -x
fi

START=$( nanos )
echo "TURBINE-PBS"
echo "TURBINE_HOME: ${TURBINE_HOME}"
echo

NODES=getenv(NODES)
PROCS=getenv(PROCS)
PPN=getenv(PPN)

# On Polaris, provide PROCS/PPN to mpiexec,
#             and turn off MPICH GPU support
m4_ifelse(getenv(TURBINE_POLARIS),1,
TURBINE_LAUNCH_OPTIONS=( getenv(TURBINE_LAUNCH_OPTIONS)
                         -n ${PROCS} --ppn ${PPN:-1} )
export MPICH_GPU_SUPPORT_ENABLED=0
)

TURBINE_LAUNCHER=getenv(TURBINE_LAUNCHER)
COMMAND=( getenv(COMMAND) )
TURBINE_OUTPUT=getenv(TURBINE_OUTPUT)

cd ${TURBINE_OUTPUT}

# Restore user PYTHONPATH if the system overwrote it:
export PYTHONPATH=getenv(PYTHONPATH)

# BEGIN TURBINE_PRELAUNCH
getenv(TURBINE_PRELAUNCH)
# END TURBINE_PRELAUNCH

export LD_LIBRARY_PATH=getenv(LD_LIBRARY_PATH):getenv(TURBINE_LD_LIBRARY_PATH)
source ${TURBINE_HOME}/scripts/helpers.sh
source ${TURBINE_HOME}/scripts/turbine-config.sh

# PBS exports all environment variables to the job under #PBS -V
# Evaluate any user turbine -e K=V settings here
export getenv(USER_ENV_CODE)

log_path LD_LIBRARY_PATH
echo

if (
  turbine_log_start | tee -a turbine.log
  turbine_report_env > turbine-env.txt
  echo
  set -x
  # Run Turbine!
  ${TURBINE_LAUNCHER} \
    ${TURBINE_LAUNCH_OPTIONS[@]} ${TURBINE_INTERPOSER:-} ${COMMAND[@]}
)
then
  CODE=0
else
  CODE=$?
  echo
  echo "TURBINE-PBS: launcher returned an error code!"
  echo
fi

echo
STOP=$( nanos )
DURATION=$( duration )

turbine_log_stop | tee -a turbine.log

# Return exit code from launcher
exit $CODE

# Local Variables:
# mode: m4
# End:
