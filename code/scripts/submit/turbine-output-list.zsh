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
# COUNT most recent runs

set -e

THIS=$( cd $( dirname $0 ) ; /bin/pwd )
TURBINE_HOME=$( cd ${THIS}/../.. ; /bin/pwd )
source ${TURBINE_HOME}/scripts/helpers.zsh

TURBINE_OUTPUT_ROOT=${TURBINE_OUTPUT_ROOT:-${HOME}/turbine-output}

set -u

usage()
{
  print "turbine-output-list <COUNT>?"
  print "shows all or COUNT most recent turbine-output directories"
}

COUNT="ALL"
if [[ ${#*} == 1 ]]
then
  COUNT=$1
elif [[ ${#*} > 1 ]]
then
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
  if [[ -f ${D}/jobid.txt ]]
  then
    JOBID=$( < ${D}/jobid.txt )
  else
    JOBID="UNKNOWN"
  fi
  printf "%7s %s\n" ${JOBID} ${D}
done

return 0
