#!/bin/zsh -f

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

# TURBINE-APRUN-RUN
# Creates a APRUN run file and runs it on the given program

# USAGE
# > VAR1=VALUE1 VAR2=VALUE2 turbine-aprun-run.zsh <PROGRAM> <ARGS>*

# ENVIRONMENT
# NODES: Number of nodes to use
# PPN:   Processes-per-node

# NOTE: See the sourced helpers.zsh script for definitions of some
#       shell functions used here.

export PROGRAM=$1

if [[ ${PROGRAM} == "" ]]
then
  print "No PROGRAM!"
  print "See the header of this script or the Swift/T Guide!"
  return 1
fi

shift
export ARGS="${*}"

print $0

export TURBINE_HOME=$( cd $(dirname $0)/../../.. && /bin/pwd )
if [[ ${?} != 0 ]]
then
  print "Could not find Turbine installation!"
  return 1
fi
declare TURBINE_HOME

source ${TURBINE_HOME}/scripts/helpers.zsh
if [[ ${?} != 0 ]]
then
  print "Broken Turbine installation!"
  declare TURBINE_HOME
  return 1
fi

# Set SCRIPT_NAME, make PROGRAM an absolute path
export SCRIPT_NAME=$( basename ${PROGRAM} )
pushd $( dirname ${PROGRAM} ) >& /dev/null
exitcode "Could not find: ${PROGRAM}"
PROGRAM_DIR=$( /bin/pwd )
popd >& /dev/null
PROGRAM=${PROGRAM_DIR}/${SCRIPT_NAME}

export WALLTIME=${WALLTIME:-00:15:00}
export PPN=${PPN:-1}

checkvars PROGRAM NODES PPN TURBINE_OUTPUT WALLTIME
declare   PROGRAM NODES PPN TURBINE_OUTPUT WALLTIME

export PROCS=$(( NODES*PPN ))

# Filter the template to create the PBS submit script
TURBINE_APRUN_M4=${TURBINE_HOME}/scripts/submit/cray/turbine-aprun.sh.m4
TURBINE_APRUN=${TURBINE_OUTPUT}/turbine-aprun.sh

mkdir -pv ${TURBINE_OUTPUT}
exitcode "Could not create TURBINE_OUTPUT directory!"
touch ${TURBINE_APRUN}
exitcode "Could not write to: ${TURBINE_APRUN}"

m4 ${TURBINE_APRUN_M4} > ${TURBINE_APRUN}
exitcode "Errors in M4 processing!"

print "wrote: ${TURBINE_APRUN}"

QUEUE_ARG=""
[[ ${QUEUE} != "" ]] && QUEUE_ARG="-q ${QUEUE}"

qsub ${=QUEUE_ARG} ${=QSUB_OPTS} ${TURBINE_OUTPUT}/turbine-aprun.sh
# Return exit code from qsub
