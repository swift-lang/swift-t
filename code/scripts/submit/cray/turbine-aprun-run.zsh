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

export PROGRAM=$1
shift
export ARGS="${*}"

TURBINE=$( which turbine )
if [[ ${?} != 0 ]]
then
  print "Could not find Turbine in PATH!"
  return 1
fi

export TURBINE_HOME=$( cd $(dirname ${TURBINE})/.. ; /bin/pwd )
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

checkvars PROGRAM NODES PPN TURBINE_OUTPUT
declare   PROGRAM NODES PPN TURBINE_OUTPUT SCRIPT_NAME

export PROCS=$(( NODES*PPN ))

${TURBINE_HOME}/scripts/submit/cray/setup-turbine-aprun.zsh \
  ${TURBINE_OUTPUT}/turbine-aprun.sh
exitcode "setup-turbine-aprun failed!"

QUEUE_ARG=""
[[ ${QUEUE} != "" ]] && QUEUE_ARG="-q ${QUEUE}"

qsub ${=QUEUE_ARG} ${=QSUB_OPTS} ${TURBINE_OUTPUT}/turbine-aprun.sh
# Return exit code from qsub
