m4_changecom(`dnl')#!/bin/bash`'bash_l()

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

# TURBINE-LSF.SH.M4
# The Turbine LSF template.  This is automatically filled in
# by M4 in turbine-lsf-run.zsh

# Created: m4_esyscmd(`date "+%Y-%m-%d %H:%M:%S"')

m4_ifelse(getenv(PROJECT), `',,
#BSUB -P getenv(PROJECT))
m4_ifelse(getenv(QUEUE), `',,
#BSUB -q getenv(QUEUE))
#BSUB -J getenv(TURBINE_JOBNAME)
#BSUB -nnodes getenv(NODES)
#BSUB -W getenv(WALLTIME)
#BSUB -e getenv(OUTPUT_FILE)
#BSUB -o getenv(OUTPUT_FILE)
#BSUB -cwd getenv(TURBINE_OUTPUT)

# User directives:
# BEGIN TURBINE_DIRECTIVE
getenv(TURBINE_DIRECTIVE)
# END TURBINE_DIRECTIVE

set -eu

VERBOSE=getenv(VERBOSE)
if (( ${VERBOSE} ))
then
 set -x
fi

echo "TURBINE-LSF"
echo "TURBINE: DATE START: $( date "+%Y-%m-%d %H:%M:%S" )"
echo

TURBINE_OUTPUT=getenv(TURBINE_OUTPUT)
TURBINE_HOME=getenv(TURBINE_HOME)
COMMAND="getenv(COMMAND)"
PROCS=getenv(PROCS)
PPN=getenv(PPN)

# Restore user PYTHONPATH if the system overwrote it:
export PYTHONPATH=getenv(PYTHONPATH)
# Add Turbine Python utilities:
PYTHONPATH=$PYTHONPATH:${TURBINE_HOME}/py

# Construct jsrun-formatted user environment variable arguments
# The dummy is needed for old GNU bash (4.2.46, Summit) under set -eu
USER_ENV_ARRAY=( getenv(USER_ENV_ARRAY) )
USER_ENV_COUNT=${#USER_ENV_ARRAY[@]}
USER_ENV_ARGS=( -E _dummy=x )
USER_ENV_ARGS=( -E PYTHONPATH )
for (( i=0 ; i < USER_ENV_COUNT ; i+=2 ))
do
  K=${USER_ENV_ARRAY[i]}
  V=${USER_ENV_ARRAY[i+1]}
  KV="$K=$V"
  USER_ENV_ARGS+=( -E "${KV}" )
done

export LD_LIBRARY_PATH=getenv(LD_LIBRARY_PATH):getenv(TURBINE_LD_LIBRARY_PATH)
source ${TURBINE_HOME}/scripts/turbine-config.sh

cd ${TURBINE_OUTPUT}

# User prelaunch commands:
# BEGIN TURBINE_PRELAUNCH
getenv(TURBINE_PRELAUNCH)
# END TURBINE_PRELAUNCH

# Deduplicate entries in LD_LIBRARY_PATH to reduce size
# for systems that expand environment variables on the command line
LLP_OLD=$LD_LIBRARY_PATH
LLP_NEW=""
for P in ${LLP_OLD//:/ }
do
  # Append colon here to prevent prefix matching:
  if [[ ! $LLP_NEW =~ $P: ]]
  then
     LLP_NEW+=$P:
  fi
done

if (( ${#LLP_OLD} != ${#LLP_NEW} ))
then
    echo "turbine-lsf: changed LD_LIBRARY_PATH ..."
    echo "turbine-lsf: from:"
    echo $LLP_OLD | tr : '\n' | nl
    echo "turbine-lsf: to:"
    echo $LLP_NEW | tr : '\n' | nl
    LD_LIBRARY_PATH=$LLP_NEW
fi

TURBINE_LAUNCH_OPTIONS=( -n $PROCS -r $PPN getenv(TURBINE_LAUNCH_OPTIONS) )

START=$( date +%s.%N )
if (
   # Dump the environment to a sorted file for debugging:
   printenv -0 | sort -z | tr '\0' '\n' > turbine-env.txt
   set -x
   # Launch it!
   jsrun ${TURBINE_LAUNCH_OPTIONS[@]} \
            -E TCLLIBPATH \
            -E ADLB_PRINT_TIME=1 \
            "${USER_ENV_ARGS[@]}" \
            ${COMMAND}
)
then
    CODE=0
else
    CODE=$?
    echo
    echo "TURBINE-LSF: jsrun returned an error code!"
    echo
fi
echo
echo "TURBINE: EXIT CODE: $CODE"
STOP=$( date +%s.%N )

# Bash cannot do floating point arithmetic:
DURATION=$( awk -v START=${START} -v STOP=${STOP} \
            'BEGIN { printf "%.3f\n", STOP-START }' < /dev/null )

echo
echo "TURBINE: MPIEXEC TIME: ${DURATION}"
echo "TURBINE: DATE STOP:  $( date "+%Y-%m-%d %H:%M:%S" )"
exit $CODE
