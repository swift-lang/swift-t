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

ifelse(getenv_nospace(QUEUE),`',,
#SBATCH --partition=getenv(QUEUE)
)

ifelse(getenv_nospace(PROJECT),`',,
#SBATCH --account=getenv(PROJECT)
)

#SBATCH --job-name=getenv_nospace(TURBINE_JOBNAME)

#SBATCH --time=getenv_nospace(WALLTIME)
#SBATCH --nodes=getenv_nospace(NODES)
#SBATCH --ntasks-per-node=getenv_nospace(PPN)
#SBATCH -D=getenv_nospace(TURBINE_OUTPUT)

# M4 conditional to optionally perform user email notifications
ifelse(getenv_nospace(MAIL_ENABLED),`1',
#SBATCH --mail-user=getenv_nospace(MAIL_ADDRESS)
#SBATCH --mail-type=ALL
)

# This block should be here, after other arguments to #SBATCH, so that the user can overwrite automatically set values such as --nodes (which is set in run-init.zsh using PROCS / PPN)
# Note this works because sbatch ignores all but the last of duplicate arguments
# TURBINE_SBATCH_ARGS could include --exclusive, --constraint=..., etc.
ifelse(getenv_nospace(TURBINE_SBATCH_ARGS),`',,
#SBATCH getenv(TURBINE_SBATCH_ARGS)
)

# BEGIN TURBINE_DIRECTIVE
getenv(TURBINE_DIRECTIVE)
# END TURBINE_DIRECTIVE

echo TURBINE-SLURM.SH
START=$( date "+%s.%N" )
echo "START: $( date '+%Y-%m-%d %H:%M:%S' )"

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

# BEGIN TURBINE_PRELAUNCH
getenv(TURBINE_PRELAUNCH)
# END TURBINE_PRELAUNCH

# Use this on Midway:
# module load openmpi gcc/4.9


# Use this on Bebop:
# module load icc
# module load mvapich2

# Use mpiexec on Midway
# TURBINE_LAUNCHER="getenv(TURBINE_LAUNCHER)"
# TURBINE_LAUNCHER=${TURBINE_LAUNCHER:-mpiexec}
# TURBINE_INTERPOSER="getenv(TURBINE_INTERPOSER)"

# Use this on Cori:
# TURBINE_LAUNCHER=srun
# module swap PrgEnv-intel PrgEnv-gnu
# module load gcc

(
echo
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
