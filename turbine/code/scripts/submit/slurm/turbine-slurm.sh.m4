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

# TURBINE-SLURM.SH

# Created: esyscmd(`date')

# Define a convenience macro
# This simply does environment variable substition when m4 runs
define(`getenv', `esyscmd(printf -- "$`$1'")')
define(`getenv_nospace', `esyscmd(printf -- "$`$1'")')

#SBATCH --time=getenv(WALLTIME)
#SBATCH --nodes=getenv(NODES)
#SBATCH --ntasks-per-node=getenv(PPN)
#SBATCH --workdir=getenv(TURBINE_OUTPUT)
#SBATCH --output=getenv(OUTPUT_FILE)
#SBATCH --error=getenv(OUTPUT_FILE)

echo TURBINE-SLURM.SH

VERBOSE=getenv(VERBOSE)
if (( ${VERBOSE} ))
then
 set -x
fi

TURBINE_HOME=getenv(TURBINE_HOME)
source ${TURBINE_HOME}/scripts/turbine-config.sh

COMMAND="getenv(COMMAND)"

${TURBINE_LAUNCHER} ${COMMAND}
# Return exit code from mpirun
