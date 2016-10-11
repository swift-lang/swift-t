changecom(`dnl')#!/bin/bash -e
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

# TURBINE-APRUN.SH

# Created: esyscmd(`date')

# Define a convenience macro
# This simply does environment variable substition when m4 runs
define(`getenv', `esyscmd(printf -- "$`$1'")')

#PBS -N getenv(TURBINE_JOBNAME)
ifelse(getenv(PROJECT), `',,
#PBS -A getenv(PROJECT))
ifelse(getenv(QUEUE), `',,
#PBS -q getenv(QUEUE))
#PBS -l walltime=getenv(WALLTIME)
#PBS -o getenv(OUTPUT_FILE)

### Set the job size using appropriate directives for this system
ifelse(getenv(CRAY_PPN), `true',
### Blue Waters mode
ifelse(getenv(CRAY_FEATURE), `',
#PBS -l nodes=getenv(NODES):ppn=getenv(PPN),
#PBS -l nodes=getenv(NODES):ppn=getenv(PPN):getenv(CRAY_FEATURE)
),
ifelse(getenv(TITAN), `true',
#PBS -l nodes=getenv(NODES),
### Default aprun mode
#PBS -l mppwidth=getenv(PROCS)
#PBS -l mppnppn=getenv(PPN)))
### End job size directives selection

# This is ineffective- we have to use 'aprun -e'
# PBS -V

# Merge stdout/stderr
#PBS -j oe
# Disable mail
#PBS -m n

VERBOSE=getenv(VERBOSE)
(( VERBOSE )) && set -x

# Set variables required for turbine-config.sh
export TURBINE_HOME=getenv(TURBINE_HOME)
TURBINE_STATIC_EXEC=getenv(TURBINE_STATIC_EXEC)
EXEC_SCRIPT=getenv(EXEC_SCRIPT)

# Setup configuration for turbine
source ${TURBINE_HOME}/scripts/turbine-config.sh

SCRIPT=getenv(SCRIPT)
ARGS="getenv(ARGS)"
NODES=getenv(NODES)
WALLTIME=getenv(WALLTIME)
TURBINE_OUTPUT=getenv(TURBINE_OUTPUT)

export TURBINE_USER_LIB=getenv(TURBINE_USER_LIB)
export TURBINE_LOG=getenv(TURBINE_LOG)
export TURBINE_DEBUG=getenv(TURBINE_DEBUG)
export ADLB_DEBUG=getenv(ADLB_DEBUG)
export PATH=getenv(PATH)

# Set configuration of Turbine processes
export ADLB_SERVERS=getenv(ADLB_SERVERS)
# Default to 1
ADLB_SERVERS=${ADLB_SERVERS:-1}
export TURBINE_GEMTC_WORKER=getenv(TURBINE_GEMTC_WORKER)

export ADLB_DEBUG_RANKS=getenv(ADLB_DEBUG_RANKS)
export ADLB_PRINT_TIME=getenv(ADLB_PRINT_TIME)
export MPICH_RANK_REORDER_METHOD=getenv(MPICH_RANK_REORDER_METHOD)

# Output header
echo "Turbine: turbine-cray.sh"
date "+%m/%d/%Y %I:%M%p"
echo

PROCS=getenv(`PROCS')
TURBINE_WORKERS=$(( ${PROCS} - ${ADLB_SERVERS} ))

cd ${TURBINE_OUTPUT}

SCRIPT_NAME=$( basename ${SCRIPT} )

# Put environment variables from run-init into 'aprun -e' format
ENVS=""
for KV in ${ENV_PAIRS[@]}
do
    echo KV $KV
    ENVS+="-e ${KV} "
done

echo ENVS $ENVS

OUTPUT_FILE=getenv(OUTPUT_FILE)
if [ -z "$OUTPUT_FILE" ]
then
    echo "JOB OUTPUT:"
    echo
    aprun -n getenv(PROCS) -N getenv(PPN) ${APRUN_ENV} -cc none -d 1 \
          ${TCLSH} ${SCRIPT_NAME} ${ARGS}
else
    # Stream output to file for immediate viewing
    echo "JOB OUTPUT is in ${OUTPUT_FILE}.${PBS_JOBID}.out"
    echo "Running: ${TCLSH} ${SCRIPT_NAME} ${ARGS}"
    aprun -n getenv(PROCS) -N getenv(PPN) ${ENVS} -cc none -d 1 \
          ${TCLSH} ${SCRIPT_NAME} ${ARGS} \
                     2>&1 > "${OUTPUT_FILE}.${PBS_JOBID}.out"
fi

# Local Variables:
# mode: m4
# End:
