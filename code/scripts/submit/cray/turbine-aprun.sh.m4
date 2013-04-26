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

# TURBINE-APRUN.SH

# Created: esyscmd(`date')

# Define a convenience macro
define(`getenv', `esyscmd(printf -- "$`$1' ")')

#PBS -N TURBINE
#PBS -l walltime=getenv(WALLTIME)
#PBS -l mppwidth=getenv(PROCS)
#PBS -l mppnppn=getenv(PPN)
#PBS -o getenv(TURBINE_OUTPUT)

# Merge stdout/stderr
#PBS -j oe
# Disable mail
#PBS -m n

# Receive some parameters
PROGRAM=getenv(`PROGRAM')
ARGS="getenv(`ARGS')"
NODES=getenv(`NODES')
WALLTIME=getenv(`WALLTIME')
TURBINE_OUTPUT=getenv(TURBINE_OUTPUT)
export TURBINE_HOME=getenv(`TURBINE_HOME')
export TURBINE_LOG=getenv(`TURBINE_LOG')
export PATH=getenv(PATH)

# Set configuration of Turbine processes
export TURBINE_ENGINES=getenv(TURBINE_ENGINES)
export ADLB_SERVERS=getenv(ADLB_SERVERS)
# Default to 1
TURBINE_ENGINES=${TURBINE_ENGINES:-1}
ADLB_SERVERS=${ADLB_SERVERS:-1}

export ADLB_PRINT_TIME=getenv(ADLB_PRINT_TIME)

# Output header
echo "Turbine: turbine-aprun.sh"
date "+%m/%d/%Y %I:%M%p"
echo

# Log the parameters
echo "TURBINE_HOME: ${TURBINE_HOME}"
echo "PROGRAM:      ${PROGRAM} ${ARGS}"
echo "NODES:        ${NODES}"
echo "WALLTIME:     ${WALLTIME}"
echo
echo "TURBINE_ENGINES: ${TURBINE_ENGINES}"
echo "ADLB_SERVERS:    ${ADLB_SERVERS}"
echo
echo "JOB OUTPUT:"
echo

# Be sure we are in an accessible directory
cd ${TURBINE_OUTPUT}

source ${TURBINE_HOME}/scripts/turbine-config.sh
if [[ ${?} != 0 ]]
then
  echo "turbine: configuration error!"
  exit 1
fi

# Send environment variables to PBS job:
#PBS -v PATH TURBINE_ENGINES ADLB_SERVERS TURBINE_HOME
# USER: Set aprun parameters to agree with PBS -l settings
aprun -n getenv(PROCS) -N getenv(PPN) -cc none -d 1 ${TCLSH} ${PROGRAM} ${ARGS}
