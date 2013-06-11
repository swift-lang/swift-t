#!/bin/zsh -f
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

# SETUP-TURBINE-APRUN
# Filters the Turbine APRUN template turbine-aprun.sh.m4
# This is done by simply using environment variables
# in M4 to create the Turbine APRUN submit file (turbine-aprun.sh)

# USAGE
# > VAR1=VALUE1 VAR2=VALUE2 setup-turbine-aprun <output>?
# If output is not specified, uses ./turbine-aprun.sh
# Required variables are:
#    PROGRAM: The Turbine program to run
# Recognized variables are:
#    WALLTIME: The PBS walltime as HH:MM:SS (default 15min)
# Then, run sbatch <options> turbine-aprun.sh

TURBINE=$( which turbine )
if [[ ${?} != 0 ]]
then
  print "Could not find Turbine in PATH!"
  return 1
fi

export TURBINE_HOME=$( cd $(dirname ${TURBINE})/.. ; /bin/pwd )

source ${TURBINE_HOME}/scripts/helpers.zsh
if [[ ${?} != 0 ]]
then
  print "Broken Turbine installation!"
  declare TURBINE_HOME
  return 1
fi

checkvars PROGRAM
declare PROGRAM

