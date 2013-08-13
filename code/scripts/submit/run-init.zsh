
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

# RUN-INIT

# Common submission setup file used by Cobalt and PBS
# Used to process command line arguments, initialize basic settings
# before launching qsub

source ${TURBINE_HOME}/scripts/turbine-config.sh
source ${TURBINE_HOME}/scripts/helpers.zsh

# Defaults:
export PROCS=0
SETTINGS=0
WALLTIME=${WALLTIME:-00:15:00}
TURBINE_OUTPUT_ROOT=${HOME}/turbine-output
VERBOSE=0

# Place to store output directory name
OUTPUT_TOKEN_FILE=turbine-cobalt-directory.txt

# Job environment
typeset -T ENV env
env=()

# Get options
while getopts "d:e:n:o:s:t:V" OPTION
 do
  case ${OPTION}
   in
    d)
      OUTPUT_TOKEN_FILE=${OPTARG}
      ;;
    e) env+=${OPTARG}
      ;;
    n) PROCS=${OPTARG}
      ;;
    o) TURBINE_OUTPUT_ROOT=${OPTARG}
      ;;
    s) SETTINGS=${OPTARG}
      ;;
    t) WALLTIME=${OPTARG}
      ;;
    V)
      VERBOSE=1
      ;;
    *)
      print "abort"
      exit 1
      ;;
  esac
done
shift $(( OPTIND-1 ))

if (( VERBOSE ))
then
  set -x
fi

SCRIPT=$1
shift
ARGS=${*}

if [[ ${SETTINGS} != 0 ]]
then
  declare SETTINGS
  source ${SETTINGS}
  exitcode "error in settings: ${SETTINGS}"
fi

checkvars QUEUE SCRIPT

START=$( date +%s )

[[ ${PROCS} != 0 ]] || abort "PROCS==0"

RUN=$( date_path )

# Create the directory in which to run
TURBINE_OUTPUT=${TURBINE_OUTPUT_ROOT}/${RUN}
declare TURBINE_OUTPUT
print ${TURBINE_OUTPUT} > ${OUTPUT_TOKEN_FILE}
mkdir -p ${TURBINE_OUTPUT}
exitcode "mkdir failed: ${TURBINE_OUTPUT}"

# All output from job, including error stream
export OUTPUT_FILE=${TURBINE_OUTPUT}/output.txt
