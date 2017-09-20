#!/bin/zsh -f
set -eu

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

# TURBINE-CRAY-RUN
# Creates an APRUN run file and runs it on the given program

# MPICH_CUSTOM_RANK_ORDER: executable that prints Mpich rank order file
#          to standard output, for MPICH_RANK_REORDER_METHOD=3

print "TURBINE-CRAY SCRIPT"

export TURBINE_HOME=$( cd $( dirname $0 )/../../.. ; /bin/pwd )
if [[ ${?} != 0 ]]
then
  print "Could not find Turbine installation!"
  return 1
fi
# declare TURBINE_HOME

source ${TURBINE_HOME}/scripts/submit/run-init.zsh

# Setup custom rank order
if (( ${+MPICH_CUSTOM_RANK_ORDER} ))
then
  if [[ ! -x "${MPICH_CUSTOM_RANK_ORDER}" ]]
  then
    print "Expected MPICH_CUSTOM_RANK_ORDER=${MPICH_CUSTOM_RANK_ORDER} to \
           be an executable file.  Aborting."
    exit 1
  fi

  ${MPICH_CUSTOM_RANK_ORDER} ${NODES} > ${TURBINE_OUTPUT}/MPICH_RANK_ORDER
  export MPICH_RANK_REORDER_METHOD=3
fi

# Filter the template to create the PBS submit script
TURBINE_CRAY_M4=${TURBINE_HOME}/scripts/submit/cray/turbine-cray.sh.m4
TURBINE_CRAY=${TURBINE_OUTPUT}/turbine-cray.sh
m4 ${TURBINE_CRAY_M4} > ${TURBINE_CRAY}
chmod u+x ${TURBINE_CRAY}
print "wrote: ${TURBINE_CRAY}"

# If the user specified a queue, we use it:
QUEUE_ARG=""
if (( ${+QUEUE} ))
then
  QUEUE_ARG="-q ${QUEUE}"
fi

# Convert any user turbine-cray-run -e K=V settings to qsub -e K=V:
export APRUN_ENV=''
for kv in ${env}
do
  print "turbine: user environment variable: ${kv}"
  APRUN_ENV+="-e ${kv} "
done

(( ! ${+QSUB_OPTS} )) && QSUB_OPTS=""

# Read all output from qsub
QSUB_OUT=""
qsub ${=QUEUE_ARG} ${=QSUB_OPTS} ${TURBINE_OUTPUT}/turbine-cray.sh | \
  while read T ; do QSUB_OUT+="${T} " ; done

# Did we get a job number?
# Break output into words:
QSUB_OUT_ARRAY=( ${=QSUB_OUT} )
if (( ${#QSUB_OUT_ARRAY} == 0 ))
then
  print
  print "turbine: error: received nothing from qsub!"
  return 1
fi
QSUB_OUT_WORD1=${QSUB_OUT_ARRAY[1]}
# Chop off anything after a dot
QSUB_OUT_WORD1_PFX=${QSUB_OUT_WORD1%.*}
if [[ ${QSUB_OUT_WORD1_PFX} != <-> ]]
then
  print
  print "turbine: error: received invalid job ID from qsub!"
  print "turbine: received:"
  print ${QSUB_OUT_ARRAY} | fmt -w 60
  return 1
fi

JOB_ID=$( print ${QSUB_OUT} | tr -d " " ) # Trim
declare JOB_ID
print ${JOB_ID} > ${TURBINE_OUTPUT}/jobid.txt

turbine_log >> ${LOG_FILE}
