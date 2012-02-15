#!/bin/zsh

# Variables that may have defaults set in the environment:
# QUEUE, TURBINE_OUTPUT_ROOT, PROJECT

TURBINE_HOME=$( cd $( dirname $0 )/../../.. ; /bin/pwd )
print "TURBINE_HOME: ${TURBINE_HOME}"
source ${TURBINE_HOME}/scripts/turbine-config.sh

# Defaults:
PROCS=0
WALLTIME="00:15:00"

# Job environment
typeset -T ENV env
env=()

while getopts "e:n:o:t:" OPTION
 do
 case ${OPTION}
   in
   e) env+=${OPTARG}
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
START=$( date +%s )

checkvars QUEUE SCRIPT

if [[ ${TURBINE_OUTPUT_ROOT} == "" ]]
then
  TURBINE_OUTPUT_ROOT=${HOME}/turbine-output
fi

RUN=$( date_path )

TURBINE_OUTPUT=${TURBINE_OUTPUT_ROOT}/${RUN}
print "TURBINE_OUTPUT: ${TURBINE_OUTPUT}"
mkdir -pv ${TURBINE_OUTPUT}

LOG=${TURBINE_OUTPUT}/log.txt

print "SCRIPT: ${SCRIPT}" >> ${LOG}
cp -v ${SCRIPT} ${TURBINE_OUTPUT}
SCRIPT_NAME=$( basename ${SCRIPT} )

JOB_ID_FILE=${TURBINE_OUTPUT}/jobid.txt

TURBINE_ENGINES=$(( 1 )) # PROCS/128 ))
ADLB_SERVERS=$((    1 )) # PROCS/128 ))

# Turbine-specific environment
env+=( TCLLIBPATH=${TCLLIBPATH}
       DEBUG=1
       LOGGING=1
       TURBINE_ENGINES=${TURBINE_ENGINES}
       ADLB_SERVERS=${ADLB_SERVERS}
)

NODES=$(( PROCS/4 ))
cqsub -n ${NODES} \
      -t ${WALLTIME} \
      -q ${QUEUE} \
      -C ${TURBINE_OUTPUT} \
      -e ${ENV} \
      -m vn \
      ${TCLSH} ${SCRIPT_NAME} | read JOB_ID

if [[ ${JOB_ID} == "" ]]
then
  print "cqsub failed!"
  exit 1
fi

print "JOB: ${JOB_ID}"
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
print "DONE"
