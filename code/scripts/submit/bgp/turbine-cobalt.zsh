#!/bin/zsh

# Variables that may have defaults set in the environment: 
# QUEUE, TURBINE_OUTPUT_ROOT

TURBINE_HOME=$( cd $( dirname $0 )/../../.. ; /bin/pwd )
print "TURBINE_HOME: ${TURBINE_HOME}"
source ${TURBINE_HOME}/scripts/turbine-config.sh

# Defaults:
PROCS=0
WALLTIME="00:15:00"

while getopts "n:o:t:" OPTION
 do
 case ${OPTION}
   in
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

checkvars QUEUE SCRIPT
  
if [[ ${TURBINE_OUTPUT_ROOT} == "" ]]
then
  TURBINE_OUTPUT_ROOT=${HOME}/turbine-output
fi

# For /bin/date (Intrepid is on UTC):
export TZ=CST6CDT

RUN=$( date_path )

TURBINE_OUTPUT=${TURBINE_OUTPUT_ROOT}/${RUN}
print "TURBINE_OUTPUT: ${TURBINE_OUTPUT}"
mkdir -pv ${TURBINE_OUTPUT}

LOG=${TURBINE_OUTPUT}/log.txt

print "SCRIPT: ${SCRIPT}" >> ${LOG}
cp -v ${SCRIPT} ${TURBINE_OUTPUT}
SCRIPT_NAME=$( basename ${SCRIPT} )

JOB_ID_FILE=${TURBINE_OUTPUT}/jobid.txt

typeset -T ENV env 
env=( TCLLIBPATH=${TCLLIBPATH}
      DEBUG=0
      LOGGING=0
      TURBINE_ENGINES=$((PROCS/4))
      ADLB_SERVERS=$((PROCS/4))
)

NODES=$(( PROCS/4 ))
cqsub -n ${NODES} \
      -t ${WALLTIME} \
      -q ${QUEUE} \
      -C ${TURBINE_OUTPUT} \
      -e ${ENV} \
      -m vn \
      ${TCLSH} ${SCRIPT_NAME} | read JOB_ID

print "JOB: ${JOB_ID}"
{
  print "PROCS: ${PROCS}" 
  print "SUBMITTED: $( date_nice )" 
  print "TURBINE_ENGINES: ${TURBINE_ENGINES}" 
  print "ADLB_SERVERS: ${ADLB_SERVERS}"
} >> ${LOG}

print ${JOB_ID} > ${JOB_ID_FILE}

cqwait ${JOB_ID}

print "COMPLETE: $( date_nice )" >> ${LOG}
print "DONE"
