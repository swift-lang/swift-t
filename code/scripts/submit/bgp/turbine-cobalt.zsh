#!/bin/zsh -f

# usage:
#  turbine-cobalt -n <PROCS> [-e <ENV>]* [-o <OUTPUT>] -t <WALLTIME>
#                 <SCRIPT> [<ARG>]*

# Variables that may have defaults set in the environment:
# PROJECT, QUEUE, TURBINE_OUTPUT_ROOT

TURBINE_HOME=$( cd $( dirname $0 )/../../.. ; /bin/pwd )
declare TURBINE_HOME
source ${TURBINE_HOME}/scripts/turbine-config.sh
source ${TURBINE_HOME}/scripts/helpers.zsh

# Defaults:
PROCS=0
WALLTIME=${WALLTIME:-00:15:00}
TURBINE_OUTPUT_ROOT=${HOME}/turbine-output

# Place to store output directory name
OUTPUT_TOKEN_FILE=output.txt

# Job environment
typeset -T ENV env
env=()

# Defaults:
VERBOSE=0

while getopts "d:e:n:o:t:v" OPTION
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
   t) WALLTIME=${OPTARG}
     ;;
   v)
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

START=$( date +%s )

checkvars QUEUE SCRIPT

[[ ${PROCS} != 0 ]]
exitcode "PROCS==0"

RUN=$( date_path )

TURBINE_OUTPUT=${TURBINE_OUTPUT_ROOT}/${RUN}
declare TURBINE_OUTPUT
print ${TURBINE_OUTPUT} > ${OUTPUT_TOKEN_FILE}
mkdir -p ${TURBINE_OUTPUT}
exitcode "mkdir failed: ${TURBINE_OUTPUT}"

LOG=${TURBINE_OUTPUT}/log.txt

print "SCRIPT: ${SCRIPT}" >> ${LOG}
SCRIPT_NAME=$( basename ${SCRIPT} )
[[ -f ${SCRIPT} ]]
exitcode "script not found: ${SCRIPT}"
cp ${SCRIPT} ${TURBINE_OUTPUT}
exitcode "copy failed: ${SCRIPT} -> ${TURBINE_OUTPUT}"

JOB_ID_FILE=${TURBINE_OUTPUT}/jobid.txt

source ${TURBINE_HOME}/scripts/turbine-config.sh
exitcode "turbine-config.sh failed!"

# Turbine-specific environment
TURBINE_ENGINES=${TURBINE_ENGINES:-1}
ADLB_SERVERS=${ADLB_SERVERS:-1}
TURBINE_WORKERS=$(( PROCS - TURBINE_ENGINES - ADLB_SERVERS ))
ADLB_EXHAUST_TIME=${ADLB_EXHAUST_TIME:-5}
TURBINE_LOG=${TURBINE_LOG:-1}
TURBINE_DEBUG=${TURBINE_DEBUG:-1}
ADLB_DEBUG=${ADLB_DEBUG:-1}

env+=( TCLLIBPATH="${TCLLIBPATH}"
       TURBINE_ENGINES=${TURBINE_ENGINES}
       TURBINE_WORKERS=${TURBINE_WORKERS}
       ADLB_SERVERS=${ADLB_SERVERS}
       ADLB_EXHAUST_TIME=${ADLB_EXHAUST_TIME}
       TURBINE_LOG=${TURBINE_LOG}
       TURBINE_DEBUG=${TURBINE_DEBUG}
       ADLB_DEBUG=${ADLB_DEBUG}
       MPIRUN_LABEL=1
)

declare SCRIPT_NAME

NODES=$(( PROCS/4 ))
(( PROCS % 4 )) && (( NODES++ ))
declare NODES
qsub -n ${NODES} \
     -t ${WALLTIME} \
     -q ${QUEUE} \
     --cwd ${TURBINE_OUTPUT} \
     --env "${ENV}" \
     --mode vn \
      ${TCLSH} ${SCRIPT_NAME} ${ARGS} | read JOB_ID

if [[ ${JOB_ID} == "" ]]
then
  print "cqsub failed!"
  exit 1
fi

declare JOB_ID
{
  print "JOB: ${JOB_ID}"
  print "PROCS: ${PROCS}"
  print "SUBMITTED: $( date_nice )"
  print "TURBINE_ENGINES: ${TURBINE_ENGINES}"
  print "TURBINE_WORKERS: ${TURBINE_WORKERS}"
  print "ADLB_SERVERS:    ${ADLB_SERVERS}"
} >> ${LOG}

print ${JOB_ID} > ${JOB_ID_FILE}

cqwait ${JOB_ID}

print "COMPLETE: $( date_nice )" >> ${LOG}
STOP=$( date +%s )
TOOK=$( tformat $(( STOP-START )) )
declare TOOK

JOB_ERROR=${TURBINE_OUTPUT}/${JOB_ID}.error
[[ -f ${JOB_ERROR} ]]
exitcode "No job error file: expected: ${JOB_ERROR}"
# Report non-zero job result codes
grep "job result code:" ${JOB_ERROR} | grep -v "code: 0"
if [[ $pipestatus[2] != 1 ]]
then
  print "JOB CRASHED"
  exit 1
fi

exit 0
