#!/usr/bin/env zsh
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
# COUNT most recent runs

set -e

THIS=$( cd $( dirname $0 ) ; /bin/pwd )
TURBINE_HOME=$( cd ${THIS}/../.. ; /bin/pwd )
source ${TURBINE_HOME}/scripts/helpers.zsh

TURBINE_OUTPUT_ROOT=${TURBINE_OUTPUT_ROOT:-${HOME}/turbine-output}

set -u

usage()
{
  print "usage:"
  print "turbine-output-list [-dj] <COUNT>?"
  print "shows all or COUNT most recent turbine-output" \
        "job IDs and directories"
  print -- "-d: directories only"
  print -- "-j: jobs only"
}

zparseopts -D d=DIRS_ONLY j=JOBS_ONLY

COUNT="ALL"
if [[ ${#*} == 1 ]]
then
  COUNT=$1
else
  print "${0:t} usage error"
  print
  usage
  exit 1
fi

# Use ZSH instead of find because of ordering
# Format YYYY/MM/DD/HH/MM/SS
if [[ ${COUNT} == "ALL" ]]
then
  print -l ${TURBINE_OUTPUT_ROOT}/*/*/*/*/*/*
else
  print -l ${TURBINE_OUTPUT_ROOT}/*/*/*/*/*/* | tail -n ${COUNT}
fi | \
while read D
do
  if [[ -z ${DIRS_ONLY} ]]
  then
    if [[ -f ${D}/jobid.txt ]]
    then
      JOB_ID=$( < ${D}/jobid.txt )
    else
      JOB_ID="UNKNOWN"
    fi
  fi
  if [[ -z ${DIRS_ONLY} && -z ${JOBS_ONLY} ]]
  then
    printf "%7s %s\n" ${JOB_ID} ${D}
  elif [[ -n ${DIRS_ONLY} ]]
  then
    print ${D}
  elif [[ -n ${JOBS_ONLY} ]]
  then
    print ${JOB_ID}
  else
    crash "internal error."
  fi
done

return 0
