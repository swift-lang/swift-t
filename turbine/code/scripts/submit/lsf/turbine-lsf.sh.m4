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

# TURBINE-LSF.SH.M4
# The Turbine LSF template.  This is automatically filled in
# by M4 in turbine-lsf-run.zsh

# Created: esyscmd(`date "+%Y-%m-%d %H:%M:%S"')

ifelse(getenv(PROJECT), `',,
#BSUB -P getenv(PROJECT))
#BSUB -J getenv(TURBINE_JOBNAME)
#BSUB -nnodes getenv_nospace(NODES)
#BSUB -W getenv(WALLTIME)
#BSUB -e getenv(OUTPUT_FILE)
#BSUB -o getenv(OUTPUT_FILE)
#BSUB -cwd getenv(TURBINE_OUTPUT)

# User directives:
# BEGIN TURBINE_DIRECTIVE
getenv(TURBINE_DIRECTIVE)
# END TURBINE_DIRECTIVE

set -eu

VERBOSE=getenv(VERBOSE)
if (( ${VERBOSE} ))
then
 set -x
fi

echo "TURBINE-LSF"
echo "DATE START: $( date "+%Y-%m-%d %H:%M" )"
echo

cd ${TURBINE_OUTPUT}

TURBINE_HOME=getenv(TURBINE_HOME)
COMMAND="getenv(COMMAND)"
PROCS=getenv(PROCS)
PPN=getenv(PPN)

USER_ENV_ARRAY=( getenv(USER_ENV_ARRAY) )

# Construct jsrun-formatted user environment variable arguments
# The dummy is needed for old GNU bash (4.2.46, Summit) under set -eu
USER_ENVS_ARGS=( -E _dummy=x )
for K in ${!USER_ENV_ARRAY[@]}
do
  USER_ENVS_ARGS+=( -E $K="${USER_ENV_ARRAY[$K]}" )
done

# Restore user PYTHONPATH if the system overwrote it:
export PYTHONPATH=getenv(PYTHONPATH)

export LD_LIBRARY_PATH=getenv(LD_LIBRARY_PATH):getenv(TURBINE_LD_LIBRARY_PATH)
source ${TURBINE_HOME}/scripts/turbine-config.sh

# User prelaunch commands:
# For Summit use:
# module load gcc/6.3.1-20170301
# module load spectrum-mpi # /10.1.0.4-20170915
# # PATH=/opt/ibm/spectrum_mpi/jsm_pmix/bin:$PATH

# BEGIN TURBINE_PRELAUNCH
getenv(TURBINE_PRELAUNCH)
# END TURBINE_PRELAUNCH

TURBINE_LAUNCH_OPTIONS=( -n $PROCS -r $PPN getenv(TURBINE_LAUNCH_OPTIONS) )

START=$( date +%s.%N )
CODE=0
if ! jsrun ${TURBINE_LAUNCH_OPTIONS[@]} \
            -E TCLLIBPATH \
            -E ADLB_PRINT_TIME=1 \
            "${USER_ENVS_ARGS[@]}" \
            ${COMMAND}
then
    CODE=$?
    echo
    echo "jsrun returned an error code!"
    echo
fi
echo
echo "EXIT CODE: $CODE"
STOP=$( date +%s.%N )

# Bash cannot do floating point arithmetic:
DURATION=$( awk -v START=${START} -v STOP=${STOP} \
            'BEGIN { printf "%.3f\n", STOP-START }' < /dev/null )

echo
echo "MPIEXEC TIME: ${DURATION}"
echo "DATE STOP:  $( date "+%Y-%m-%d %H:%M" )"
exit $CODE
