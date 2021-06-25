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

# TURBINE.PBS.M4
# Turbine PBS template.  This is automatically filled in
# by M4 in turbine-pbs-run.zsh

# Created: esyscmd(`date')

#PBS -N getenv(TURBINE_JOBNAME)
#PBS -l nodes=getenv_nospace(NODES):ppn=getenv(PPN)
#PBS -l walltime=getenv(WALLTIME)
#PBS -j oe
#PBS -o getenv(OUTPUT_FILE)
#PBS -V

# BEGIN TURBINE_DIRECTIVE
getenv(TURBINE_DIRECTIVE)
# END TURBINE_DIRECTIVE

VERBOSE=getenv(VERBOSE)
if (( ${VERBOSE} ))
then
 set -x
fi

echo "TURBINE-PBS"
date
echo

cd ${TURBINE_OUTPUT}

TURBINE_HOME=getenv(TURBINE_HOME)
COMMAND=getenv(COMMAND)

# Restore user PYTHONPATH if the system overwrote it:
export PYTHONPATH=getenv(PYTHONPATH)

export LD_LIBRARY_PATH=getenv(LD_LIBRARY_PATH):getenv(TURBINE_LD_LIBRARY_PATH)
source ${TURBINE_HOME}/scripts/turbine-config.sh

# PBS exports all environment variables to the job under #PBS -V
# Evaluate any user turbine -e K=V settings here
export getenv(USER_ENV_CODE)

START=$( date "+%s.%N" )

# Run Turbine!
${TURBINE_LAUNCHER} ${TURBINE_INTERPOSER:-} ${COMMAND}
CODE=$?

STOP=$( date "+%s.%N" )
# Bash cannot do floating point arithmetic:
DURATION=$( awk -v START=${START} -v STOP=${STOP} \
            'BEGIN { printf "%.3f\n", STOP-START }' < /dev/null )
echo "MPIEXEC TIME: ${DURATION}"

echo "CODE: ${CODE}"
echo "COMPLETE: $( date '+%Y-%m-%d %H:%M' )"

# Return exit code from launcher
exit ${CODE}

# Local Variables:
# mode: m4
# End:
