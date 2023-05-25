#!/bin/bash`'bash_l()
# We changed the M4 comment to d-n-l, not hash
# We may need 'bash -l' for the module system

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

# Created: esyscmd(`date "+%Y-%m-%d %H:%M:%S"')

#SBATCH --output=getenv(OUTPUT_FILE)
#SBATCH --error=getenv(OUTPUT_FILE)

m4_ifelse(getenv(QUEUE),`',,
#SBATCH --partition=getenv(QUEUE)
)

m4_ifelse(getenv(PROJECT),`',,
#SBATCH --account=getenv(PROJECT)
)

#SBATCH --job-name=getenv(TURBINE_JOBNAME)

#SBATCH --time=getenv(WALLTIME)
#SBATCH --nodes=getenv(NODES)
#SBATCH --ntasks-per-node=getenv(PPN)
#SBATCH -D getenv(TURBINE_OUTPUT)

# M4 conditional to optionally perform user email notifications
m4_ifelse(getenv(MAIL_ENABLED),`1',
#SBATCH --mail-user=getenv(MAIL_ADDRESS)
#SBATCH --mail-type=ALL
)

# This block should be here, after other arguments to #SBATCH, so that the user can overwrite automatically set values such as --nodes (which is set in run-init.zsh using PROCS / PPN)
# Note this works because sbatch ignores all but the last of duplicate arguments
# TURBINE_SBATCH_ARGS could include --exclusive, --constraint=..., etc.
m4_ifelse(getenv(TURBINE_SBATCH_ARGS),`',,
#SBATCH getenv(TURBINE_SBATCH_ARGS)
)

# BEGIN TURBINE_DIRECTIVE
getenv(TURBINE_DIRECTIVE)
# END TURBINE_DIRECTIVE

START=$( date "+%s.%N" )
echo "TURBINE-SLURM.SH START: $( date '+%Y-%m-%d %H:%M:%S' )"

export TURBINE_HOME=$( cd "$(dirname "$0")/../../.." ; /bin/pwd )

VERBOSE=getenv(VERBOSE)
if (( ${VERBOSE} ))
then
 set -x
fi

TURBINE_PILOT=${TURBINE_PILOT:-getenv(TURBINE_PILOT)}
if (( ! ${#TURBINE_PILOT} ))
then
  TURBINE_HOME=getenv(TURBINE_HOME)
  source ${TURBINE_HOME}/scripts/turbine-config.sh
fi

COMMAND="getenv(COMMAND)"

# SLURM exports all environment variables to the job by default
# Evaluate any user turbine -e K=V settings here
ENV_PAIRS=( getenv(USER_ENV_CODE) )
for P in "${ENV_PAIRS[@]}"
do
    export "$P"
done

# Use this on Midway:
# module load openmpi gcc/4.9
# Use mpiexec on Midway

# Use this on Bebop:
# module unload intel-mpi
# module unload intel-mkl
# module load gcc/7.1.0
# module load mvapich2
# module list
# TURBINE_LAUNCHER=srun

# Use this on Stampede2
#  TURBINE_LAUNCHER=ibrun

# Use this on Cori:
# TURBINE_LAUNCHER=srun
# module swap PrgEnv-intel PrgEnv-gnu
# module load gcc

TURBINE_LAUNCHER="getenv(TURBINE_LAUNCHER)"
TURBINE_INTERPOSER="getenv(TURBINE_INTERPOSER)"

# BEGIN TURBINE_PRELAUNCH
getenv(TURBINE_PRELAUNCH)
# END TURBINE_PRELAUNCH

if [[ ${TURBINE_LAUNCHER} == 0 ]]
then
  TURBINE_LAUNCHER=srun
fi

# Report modules to output.txt for debugging:
module list

(
  export PMI_MMAP_SYNC_WAIT_TIME=1800
  # Report the environment to a sorted file for debugging:
  printenv -0 | sort -z | tr '\0' '\n' > turbine-env.txt

  set -x
  ${TURBINE_LAUNCHER} getenv(TURBINE_LAUNCH_OPTIONS) \
                      ${TURBINE_INTERPOSER} \
                      ${COMMAND}
)
CODE=$?

STOP=$( date "+%s.%N" )
# Bash cannot do floating point arithmetic:
DURATION=$( awk -v START=${START} -v STOP=${STOP} \
            'BEGIN { printf "%.3f\n", STOP-START }' < /dev/null )

echo
echo "MPIEXEC TIME: ${DURATION}"
echo "EXIT CODE: ${CODE}"
echo "COMPLETE: $( date '+%Y-%m-%d %H:%M:%S' )"

# Return exit code from launcher
exit ${CODE}

# Local Variables:
# mode: m4;
# End:
