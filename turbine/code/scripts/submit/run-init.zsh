
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

# NOTE: See the sourced helpers.zsh script for definitions of some
#       shell functions used here.

# Common queue submission setup file.
# Used to process command line arguments, initialize basic settings
# before launching qsub or equivalent.
# Sets many environment and shell variables described below.

# This script copies the user TIC to TURBINE_OUTPUT
# then cd's to TURBINE_OUTPUT and runs from there.

# VARIABLES:
# INPUT:
#   Positional arguments: $1->SCRIPT, rest->ARGS
#   PROCS: Number of MPI processes
#   PPN: Processes-per-node: see below: (default 1)
#   WALLTIME: Formatted according to specific scheduler
#             Swift/T default: HH:MM:SS
#             LSF converts HH:MM:SS to HH:MM
#   TURBINE_OUTPUT_ROOT, TURBINE_OUTPUT_FORMAT: See sites guide
# OUTPUT:
#   SCRIPT: User-provided TIC or executable name from $1
#   ARGS:   User-provided args from ${*} after shift
#   USER_ENV_ARRAY: User environment variables for Bash array:
#                   K1 'V1' K2 'V2' ...
#   USER_ENV_CODE:  User environment variables  in Bash code:
#                   K1='V1' K2='V2' ...
#   SCRIPT_NAME=$( basename ${SCRIPT} )
#   PROGRAM=${TURBINE_OUTPUT}/${SCRIPT_NAME}  # A copy of SCRIPT to run
#   TURBINE_WORKERS
#   COMMAND="${TCLSH} ${PROGRAM} ${ARGS}"
#       or: "${PROGRAM} ${ARGS}" for executables
#   NODES: Number of nodes derived from PROCS and PPN
#   LOG_FILE:    Path to turbine.log
#   OUTPUT_FILE: Path to output.txt
#   JOB_ID_FILE: Path to jobid.txt
#   MAIL_ENABLED: If mail is enabled, 1, else 0.  Default 0.
#   MAIL_ADDRESS: If mail is enabled, an email address.
#   DRY_RUN: If 1, generate submit scripts but do not execute
# INPUT/OUTPUT:
#   TURBINE_JOBNAME
#   PROCS: may be overridden by flag -n
#   PPN:   may be set to default 1
# NORMAL SWIFT/T ENVIRONMENT VARIABLES SUPPORTED:
#   TURBINE_OUTPUT: See sites guide
#   ADLB_SERVERS
#   ADLB_EXHAUST_TIME
#   TURBINE_LOG
#   TURBINE_DEBUG
#   ADLB_DEBUG
# OTHER CONVENTIONS
#   JOB_ID: Job ID from the scheduler (not available at run time)
# UTILITIES
#   COMMON_M4: Common M4 utilities

# Files:
# Creates soft link in PWD pointing to TURBINE_OUTPUT
# Creates directory TURBINE_OUTPUT containing:
# Copy of the user TIC
# turbine-*.sh: The script to be submitted to the scheduler
# turbine.log: Summary of job metadata
# jobid.txt: The JOB_ID from the scheduler
# output.txt: The job stdout and stderr

set -eu

source ${TURBINE_HOME}/scripts/turbine-config.sh
source ${TURBINE_HOME}/scripts/helpers.zsh

# Turbine-specific environment (with defaults)
export TURBINE_JOBNAME=${TURBINE_JOBNAME:-SWIFT}
export ADLB_SERVERS=${ADLB_SERVERS:-1}
export ADLB_EXHAUST_TIME=${ADLB_EXHAUST_TIME:-1}
export TURBINE_STDOUT=${TURBINE_STDOUT:-}
export TURBINE_LOG=${TURBINE_LOG:-0}
export TURBINE_DEBUG=${TURBINE_DEBUG:-0}
export ADLB_DEBUG=${ADLB_DEBUG:-0}
export ADLB_TRACE=${ADLB_TRACE:-0}
export TCLLIBPATH=${TCLLIBPATH:-}
export WALLTIME=${WALLTIME:-00:05:00}
export PPN=${PPN:-1}
export VERBOSE=0
export ADLB_PRINT_TIME=${ADLB_PRINT_TIME:-1}

turbine_log()
# Fills in turbine.log file after job submission
{
  print "JOB:               ${JOB_ID}"
  print "COMMAND:           ${COMMAND}"
  print "TURBINE_OUTPUT:    ${TURBINE_OUTPUT}"
  print "HOSTNAME:          $( hostname -d )"
  print "SUBMITTED:         $( date_nice )"
  print "PROCS:             ${PROCS}"
  print "NODES:             ${NODES}"
  print "PPN:               ${PPN}"
  print "TURBINE_WORKERS:   ${TURBINE_WORKERS}"
  print "ADLB_SERVERS:      ${ADLB_SERVERS}"
  print "WALLTIME:          ${WALLTIME}"
  print "ADLB_EXHAUST_TIME: ${ADLB_EXHAUST_TIME}"
  print "TURBINE_HOME:      ${TURBINE_HOME}"
}

# Defaults:
PROJECT=${PROJECT:-}
QUEUE=${QUEUE:-}
CHANGE_DIRECTORY=""
export EXEC_SCRIPT=0 # 1 means execute script directly, e.g. if binary
export TURBINE_STATIC_EXEC=0 # Use turbine_sh instead of tclsh
INIT_SCRIPT=0
export PROCS=${PROCS:-0}
if (( ! ${+TURBINE_OUTPUT_ROOT} ))
then
  TURBINE_OUTPUT_ROOT=${HOME}/turbine-output
fi
SETTINGS=0
export MAIL_ENABLED=${MAIL_ENABLED:-0}
export MAIL_ADDRESS=${MAIL_ADDRESS:-0}
export DRY_RUN=0
export WAIT_FOR_JOB=0

# Place to link to output directory
# If TURBINE_OUTPUT_SOFTLINK==/dev/null , this behavior is suppressed
OUTPUT_SOFTLINK=${TURBINE_OUTPUT_SOFTLINK:-turbine-output}
# Turbine will also write the value of TURBINE_OUTPUT_HERE
OUTPUT_TOKEN_FILE=/dev/null

# Regexp for environment variable key=value pairs
ENV_RE='([A-Z0-9_]*)=(.*)'

export USER_ENV_CODE="" USER_ENV_ARRAY=""

# Get options
while getopts "d:D:e:i:M:n:o:s:t:VwxXY" OPTION
 do
  case ${OPTION}
   in
    d) CHANGE_DIRECTORY=${OPTARG}
      ;;
    D) OUTPUT_TOKEN_FILE=${OPTARG}
      ;;
    e) KV=${OPTARG}
       if [[ ${KV} =~ ${ENV_RE} ]]
       then
         USER_ENV_CODE+="${match[1]}='${match[2]}' "
         USER_ENV_ARRAY+="${match[1]} '${match[2]}' \n"
       else
         if (( ! ${(P)+KV} ))
         then
           abort "turbine: provided '-e ${KV}' but variable" \
                 "'${KV}' is not in the environment!"
         fi
         # Look up unset environment variables
         USER_ENV_CODE+="${KV}='${(P)KV}' "
         USER_ENV_ARRAY+="${KV} '${(Pq)KV}' \n"
       fi
       ;;
    i) INIT_SCRIPT=${OPTARG}
       ;;
    M) MAIL_ENABLED=1
       MAIL_ADDRESS=${OPTARG}
       ;;
    n) PROCS=${OPTARG}
       ;;
    o) TURBINE_OUTPUT_ROOT=${OPTARG}
       ;;
    s) SETTINGS=${OPTARG}
       ;;
    t) WALLTIME=${OPTARG}
       ;;
    V) VERBOSE=1
       ;;
    w) WAIT_FOR_JOB=1
       ;;
    x) export EXEC_SCRIPT=1
       ;;
    X) export TURBINE_STATIC_EXEC=1
       ;;
    Y) DRY_RUN=1
       ;;
    *) print "abort"
       exit 1
       ;;
  esac
done
shift $(( OPTIND-1 ))

if (( VERBOSE ))
then
  set -x
fi

if [[ ${PROCS} != <-> ]]
then
  print "PROCS must be an integer: you set PROCS=${PROCS}"
  return 1
fi

# Round NODES up for extra processes
#   i.e.: if there is a remainder, add an extra node
export NODES=$(( PROCS/PPN ))
(( PROCS % PPN )) && (( NODES++ )) || true
export TURBINE_WORKERS=$(( PROCS - ADLB_SERVERS ))
declare NODES PROCS PPN

export SCRIPT=$1
checkvar SCRIPT
shift
export ARGS="${*}"

if [[ ${SETTINGS} != 0 ]]
then
  print "sourcing: ${SETTINGS}"
  if ! source ${SETTINGS}
  then
    print "script failed: ${SETTINGS}"
    return 1
  fi
  print "done: ${SETTINGS}"
fi

[[ -f ${SCRIPT} ]] || abort "Could not find script: ${SCRIPT}"

START=$( date +%s )

if (( ${PROCS} == 0 ))
then
  print "The process count was not specified!"
  print "Use the -n argument or set environment variable PROCS."
  exit 1
fi

# Prints a TURBINE_OUTPUT directory
# Handles TURBINE_OUTPUT_FORMAT
# Default format is e.g., 2006/10/13/14/26/12
turbine_output_format()
{
  TURBINE_OUTPUT_FORMAT=${TURBINE_OUTPUT_FORMAT:-%Y/%m/%d/%H/%M/%S}
  local S=$( date +${TURBINE_OUTPUT_FORMAT} )
  if [[ ${S} == *%Q* ]]
  then
    # Create a unique directory by substituting on %Q
    TURBINE_OUTPUT_PAD=${TURBINE_OUTPUT_PAD:-3}
    integer -Z ${TURBINE_OUTPUT_PAD} i=1
    while true
    do
      local D=${S/\%Q/${i}}
      local TRY=${TURBINE_OUTPUT_ROOT}/${D}
      [[ ! -d ${TRY} ]] && break
      (( i++ ))
    done
    print ${TRY}
  else
    print ${TURBINE_OUTPUT_ROOT}/${S}
  fi
}

# Create the directory in which to run
if (( ! ${+TURBINE_OUTPUT} ))
then
  export TURBINE_OUTPUT=$( turbine_output_format )
fi
export TURBINE_OUTPUT
declare TURBINE_OUTPUT

mkdir -p ${TURBINE_OUTPUT}
if [[ ${OUTPUT_SOFTLINK} != /dev/null ]]
then
  [[ -L ${OUTPUT_SOFTLINK} ]] && rm ${OUTPUT_SOFTLINK}
  ln -s ${TURBINE_OUTPUT} ${OUTPUT_SOFTLINK}
fi
if [[ ${OUTPUT_TOKEN_FILE} != /dev/null ]]
then
  print ${TURBINE_OUTPUT} > ${OUTPUT_TOKEN_FILE}
fi

# All output from job, including error stream
export OUTPUT_FILE=${TURBINE_OUTPUT}/output.txt
# Create the file now so the user can tail it immediately
touch ${OUTPUT_FILE}

if [[ ${INIT_SCRIPT} != 0 ]]
then
  print "executing: ${INIT_SCRIPT}"
  if ! ${INIT_SCRIPT}
  then
    print "script failed: ${INIT_SCRIPT}"
    return 1
  fi
  print "done: ${INIT_SCRIPT}"
fi

# Log file for Turbine settings
LOG_FILE=${TURBINE_OUTPUT}/turbine.log
# All output from job, including error stream
OUTPUT_FILE=${TURBINE_OUTPUT}/output.txt

print "SCRIPT:            ${SCRIPT}" >> ${LOG_FILE}
SCRIPT_NAME=$( basename ${SCRIPT} )
export PROGRAM=${TURBINE_OUTPUT}/${SCRIPT_NAME}
# The modifier :A produces canonicalized file paths
if [[ ${SCRIPT:A} != ${PROGRAM:A} ]]
then
  cp ${SCRIPT} ${PROGRAM}
  chmod a+r ${PROGRAM}
fi
if (( TURBINE_STATIC_EXEC ))
then
  # Uses turbine_sh launcher
  export COMMAND="${TURBINE_HOME}/bin/turbine_sh ${PROGRAM} ${ARGS}"
elif (( EXEC_SCRIPT ))
then
  # User static executable
  export COMMAND="${PROGRAM} ${ARGS}"
elif [[ ${TURBINE_PILOT:-} != "" ]]
then
  export TURBINE_PILOT=${TURBINE_HOME}/bin/turbine-pilot
  export COMMAND="${TURBINE_PILOT} ${PROGRAM} ${ARGS}"
else
  # Normal case
  export COMMAND="${TCLSH} ${PROGRAM} ${ARGS}"
fi

JOB_ID_FILE=${TURBINE_OUTPUT}/jobid.txt

if (( ${MAIL_ENABLED:-0} == 1 ))
then
  if [[ ${MAIL_ADDRESS:-} == "" ]]
  then
    print "MAIL_ENABLED is on but MAIL_ADDRESS is not set!"
  fi
fi

# Anything in AUTO_VARS must have a default set above
# Keep this in sync with AUTO_VARS in bin/turbine.in
AUTO_VARS=( PROJECT QUEUE WALLTIME TURBINE_OUTPUT TURBINE_JOBNAME
            TCLLIBPATH ADLB_SERVERS TURBINE_WORKERS
            TURBINE_STDOUT
            TURBINE_LOG TURBINE_DEBUG ADLB_DEBUG ADLB_TRACE
            PATH
            )
if (( ${#TURBINE_USE_PYTHON} )) {
  AUTO_VARS+=PYTHONPATH
}
# Omitted: MPI_LABEL due to quoting issues

for NAME in ${AUTO_VARS}
do
  # (P) pulls out value by name (like bash ${!x})
  if (( ! ${(P)#NAME} )) continue
  USER_ENV_CODE+="${NAME}='${(P)NAME}' "
  # (q) quotes special characters
  USER_ENV_ARRAY+="${NAME} '${(Pq-)NAME}' \n"
done

COMMON_M4=${TURBINE_HOME}/scripts/submit/common.m4

## Local Variables:
## mode: sh
## End:
