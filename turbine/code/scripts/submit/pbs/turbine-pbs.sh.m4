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

# TURBINE PBS SH M4

# Turbine PBS template.  This is automatically filled in
# by M4 in turbine-pbs-run.zsh

# Created: esyscmd(`date "+%Y-%m-%d %H:%M:%S"')

#PBS -N getenv(TURBINE_JOBNAME)
ifelse(getenv(TURBINE_POLARIS),`1',
#PBS -l select=getenv(NODES):system=polaris ,
#PBS -l nodes=getenv_nospace(NODES):ppn=getenv(PPN))
#PBS -l walltime=getenv(WALLTIME)
#PBS -j oe
#PBS -o getenv(OUTPUT_FILE)
#PBS -V

ifelse(getenv(PROJECT),`',,
#PBS -A getenv(PROJECT)
)
ifelse(getenv(QUEUE),`',,
#PBS -q getenv(QUEUE)
)

# BEGIN TURBINE_DIRECTIVE
getenv(TURBINE_DIRECTIVE)
# END TURBINE_DIRECTIVE

VERBOSE=getenv(VERBOSE)
if (( ${VERBOSE} ))
then
 set -x
fi

set -eu

START=$( date "+%s.%N" )
echo "TURBINE-PBS.SH START: $( date '+%Y-%m-%d %H:%M:%S' )"

PROCS=getenv(PROCS)
PPN=getenv(PPN)

# On Polaris, provide PROCS/PPN to mpiexec:
ifelse(getenv(TURBINE_POLARIS),1,
TURBINE_LAUNCH_OPTIONS=( getenv(TURBINE_LAUNCH_OPTIONS) -n ${PROCS} --ppn ${PPN:-1} )
)

TURBINE_HOME=getenv(TURBINE_HOME)
TURBINE_LAUNCHER=getenv(TURBINE_LAUNCHER)
COMMAND=( getenv(COMMAND) )
TURBINE_OUTPUT=getenv(TURBINE_OUTPUT)

cd ${TURBINE_OUTPUT}

# Restore user PYTHONPATH if the system overwrote it:
export PYTHONPATH=getenv(PYTHONPATH)

export LD_LIBRARY_PATH=getenv(LD_LIBRARY_PATH):getenv(TURBINE_LD_LIBRARY_PATH)
source ${TURBINE_HOME}/scripts/turbine-config.sh

# PBS exports all environment variables to the job under #PBS -V
# Evaluate any user turbine -e K=V settings here
export getenv(USER_ENV_CODE)

(
  # Report the environment to a sorted file for debugging:
  printenv -0 | sort -z | tr '\0' '\n' > turbine-env.txt

  # Run Turbine!
  ${TURBINE_LAUNCHER} \
    ${TURBINE_LAUNCH_OPTIONS[@]} ${TURBINE_INTERPOSER:-} ${COMMAND[@]}
)
CODE=$?

STOP=$( date "+%s.%N" )
# Bash cannot do floating point arithmetic:
DURATION=$( awk -v START=${START} -v STOP=${STOP} \
            'BEGIN { printf "%.3f\n", STOP-START }' < /dev/null )
echo "MPIEXEC TIME: ${DURATION}"

echo "CODE: ${CODE}"
echo "COMPLETE: $( date '+%Y-%m-%d %H:%M:%S' )"

# Return exit code from launcher
exit ${CODE}

# Local Variables:
# mode: m4
# End:
