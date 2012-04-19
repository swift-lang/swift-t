#!/bin/zsh

# usage:
#  turbine-cobalt -n <PROCS> [-e <ENV>]* [-o <OUTPUT>] -t <WALLTIME>
#                 <SCRIPT> [<ARG>]*

# Variables that may have defaults set in the environment:
# PROJECT, QUEUE, TURBINE_OUTPUT_ROOT

TURBINE_HOME=$( cd $( dirname $0 )/../../.. ; /bin/pwd )
declare TURBINE_HOME
source ${TURBINE_HOME}/scripts/turbine-config.sh

# Defaults:
PROCS=0
WALLTIME="00:15:00"
TURBINE_OUTPUT_ROOT=${HOME}/turbine-output

# Place to store output directory name
OUTPUT_TOKEN_FILE=output.txt

# Job environment
typeset -T ENV env
env=()

while getopts "d:e:n:o:t:" OPTION
 do
 case ${OPTION}
   in
   d)
     OUTPUT_TOKEN_FILE=${OPTARG}
     ;;
   e) env+=${OPTARG}
     print a: $OPTARG
     ;;
   n) PROCS=${OPTARG}
     ;;
   o) TURBINE_OUTPUT_ROOT=${OPTARG}
     ;;
   t) WALLTIME=${OPTARG}
     ;;
   *)
     print "abort"
     exit 1
     ;;
 esac
done
shift $(( OPTIND-1 ))

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
cp -v ${SCRIPT} ${TURBINE_OUTPUT}
SCRIPT_NAME=$( basename ${SCRIPT} )

JOB_ID_FILE=${TURBINE_OUTPUT}/jobid.txt

source ${TURBINE_HOME}/scripts/turbine-config.sh
exitcode "turbine-config.sh failed!"

# Turbine-specific environment
env+=( TCLLIBPATH="${TCLLIBPATH}"
       TURBINE_DEBUG=${TURBINE_DEBUG:-1}
       ADLB_DEBUG=${ADLB_DEBUG:-1}
       LOGGING=${LOGGING:-1}
       TURBINE_ENGINES=${TURBINE_ENGINES:-1}
       ADLB_SERVERS=${ADLB_SERVERS:-1}
       ADLB_EXHAUST_TIME=${ADLB_EXHAUST_TIME:-5}
)

declare TCLSH SCRIPT_NAME

NODES=$(( PROCS/4 ))
(( PROCS % 4 )) && (( NODES++ ))
declare NODES
qsub -n ${NODES} \
     -t ${WALLTIME} \
     -q ${QUEUE} \
     --cwd ${TURBINE_OUTPUT} \
     --env "${ENV}" \
     --mode vn \
      ${TCLSH} ${SCRIPT} ${ARGS} | read JOB_ID

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
  print "ADLB_SERVERS:    ${ADLB_SERVERS}"
} >> ${LOG}

print ${JOB_ID} > ${JOB_ID_FILE}

cqwait ${JOB_ID}

print "COMPLETE: $( date_nice )" >> ${LOG}
STOP=$( date +%s )
TOOK=$( tformat $(( STOP-START )) )
declare TOOK

