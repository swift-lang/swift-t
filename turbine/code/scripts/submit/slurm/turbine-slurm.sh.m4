changecom(`dnl')#!/bin/bash -l
# We changed the M4 comment to d-n-l, not hash
# We need 'bash -l' for the module system

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

# Created: esyscmd(`date')

# Define convenience macros
# This simply does environment variable substition when m4 runs
define(`getenv', `esyscmd(printf -- "$`$1'")')
define(`getenv_nospace', `esyscmd(printf -- "$`$1'")')

#SBATCH --output=getenv(OUTPUT_FILE)
#SBATCH --error=getenv(OUTPUT_FILE)

ifelse(getenv(QUEUE),`',,
#SBATCH --partition=getenv(QUEUE)
)

ifelse(getenv(PROJECT),`',,
#SBATCH --account=getenv(PROJECT)
)

# TURBINE_SBATCH_ARGS could include --exclusive, --constraint=..., etc.
ifelse(getenv(TURBINE_SBATCH_ARGS),`',,
#SBATCH getenv(TURBINE_SBATCH_ARGS)
)

#SBATCH --job-name=getenv(TURBINE_JOBNAME)

#SBATCH --time=getenv(WALLTIME)
#SBATCH --nodes=getenv(NODES)
#SBATCH --ntasks-per-node=getenv(PPN)
#SBATCH --workdir=getenv(TURBINE_OUTPUT)

# M4 conditional to optionally perform user email notifications
ifelse(getenv(MAIL_ENABLED),`1',
#SBATCH --mail-user=getenv(MAIL_ADDRESS)
#SBATCH --mail-type=ALL
)

# User directives:
getenv(TURBINE_DIRECTIVE)

echo TURBINE-SLURM.SH

export TURBINE_HOME=$( cd "$(dirname "$0")/../../.." ; /bin/pwd )

VERBOSE=getenv(VERBOSE)
if (( ${VERBOSE} ))
then
 set -x
fi

TURBINE_HOME=getenv(TURBINE_HOME)
source ${TURBINE_HOME}/scripts/turbine-config.sh

COMMAND="getenv(COMMAND)"

# Use this on Midway:
# module load openmpi gcc/4.9

# Use this on Bebop:
module load icc
module load mvapich2

TURBINE_LAUNCHER=srun

DSS=/home/wozniak/sfw/blues/login/dataspaces-1.6.4/bin/dataspaces_server
$DSS -s $DS_SERVERS -c $DS_CLIENTS &
DS_PID=$!
# echo DS_PID: $DS_PID

echo
set -x
${TURBINE_LAUNCHER} getenv(TURBINE_LAUNCH_OPTIONS) ${VALGRIND} ${COMMAND}
# Use this for DS: -n $DS_CLIENTS
# Return exit code from mpirun
