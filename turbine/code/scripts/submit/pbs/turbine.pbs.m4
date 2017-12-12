changecom(`dnl')#!/bin/bash
# We use changecom to change the M4 comment to dnl, not hash
set -eu

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

# Define convenience macros
define(`getenv', `esyscmd(printf -- "$`$1' ")')
define(`getenv_nospace', `esyscmd(printf -- "$`$1'")')

#PBS -N getenv(TURBINE_JOBNAME)
#PBS -l nodes=getenv_nospace(NODES):ppn=getenv(PPN)
#PBS -l walltime=getenv(WALLTIME)
#PBS -j oe
#PBS -o getenv(OUTPUT_FILE)
#PBS -V

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
COMMAND="getenv(COMMAND)"

# Restore user PYTHONPATH if the system overwrote it:
export PYTHONPATH=getenv(PYTHONPATH)

export LD_LIBRARY_PATH=getenv_nospace(LD_LIBRARY_PATH):getenv(TURBINE_LD_LIBRARY_PATH)
source ${TURBINE_HOME}/scripts/turbine-config.sh

DSS=/home/wozniak/sfw/blues/login/dataspaces-1.6.4/bin/dataspaces_server
$DSS -s $DS_SERVERS -c $DS_CLIENTS &
DS_PID=$!
# echo DS_PID: $DS_PID

START=$( date +%s.%N )
${TURBINE_LAUNCHER} -l -n $DS_CLIENTS ${COMMAND}
STOP=$( date +%s.%N )
date +%s.%N

# Bash cannot do floating point arithmetic:
DURATION=$( awk -v START=${START} -v STOP=${STOP} \
            'BEGIN { printf "%.3f\n", STOP-START }' < /dev/null )
echo "MPIEXEC TIME: ${DURATION}"

kill -1 $DS_PID || true  # Ignore errors here
