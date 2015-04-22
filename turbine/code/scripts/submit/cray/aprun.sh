#!/bin/sh
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

# Sample submit script for Cray systems
# https://sites.google.com/site/exmproject/development/turbine---build#TOC-Cray-

# USAGE: qsub aprun.sh

# The user should copy and edit the parameters throughout this script
# marked USER:

# USER: Directory available from compute nodes:
USER_WORK=/lustre/beagle/wozniak

# USER: (optional) Change the qstat name
#PBS -N turbine
# USER: Set the job size
#PBS -l mppwidth=3,mppnppn=1,mppdepth=1
# USER: Set the wall time
#PBS -l walltime=10:00
# USER: (optional) Redirect output from its default location ($PWD)
#PBS -o /lustre/beagle/wozniak/pbs.out

#PBS -j oe
#PBS -m n

# USER: Set configuration of Turbine processes
export ADLB_SERVERS=1

echo "Turbine: aprun.sh"
date "+%m/%d/%Y %I:%M%p"
echo

# Be sure we are in an accessible directory
cd $PBS_O_WORKDIR

set -x
# USER: Set Turbine installation path
export TURBINE_HOME=${USER_WORK}/Public/turbine
# USER: Select program name
PROGRAM=${USER_WORK}/adlb-data.tcl

source ${TURBINE_HOME}/scripts/turbine-config.sh
if [[ ${?} != 0 ]]
then
  echo "turbine: configuration error!"
  exit 1
fi

# Send environment variables to PBS job:
#PBS -v ADLB_SERVERS TURBINE_HOME
# USER: Set aprun parameters to agree with PBS -l settings
aprun -n 3 -N 1 -cc none -d 1 ${TCLSH} ${PROGRAM}
