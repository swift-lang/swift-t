#!/bin/zsh
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

# Turbine Output Search
# for JOBID

set -e

THIS=$( cd $( dirname $0 ) ; /bin/pwd )
TURBINE_HOME=$( cd ${THIS}/../../.. ; /bin/pwd )
source ${TURBINE_HOME}/scripts/helpers.zsh

JOBID=$1

checkvar JOBID

TURBINE_OUTPUT_ROOT=${TURBINE_OUTPUT_ROOT:-${HOME}/turbine-output}

set -u

find ${TURBINE_OUTPUT_ROOT} -name jobid.txt | \
  while read ID_FILE
  do
    ID=$( < ${ID_FILE} )
    if [[ ${ID} = ${JOBID}.* ]]
    then
      print TO=$( dirname ${ID_FILE} )
      exit 0
    fi
  done

# We didn't find anything
exit 1
