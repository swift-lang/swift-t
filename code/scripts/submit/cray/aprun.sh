#!/bin/sh

# Sample submit script for Cray systems
# https://sites.google.com/site/exmproject/development/turbine---build#TOC-Cray-

# USAGE: qsub aprun.sh

# The user should copy and edit the parameters throughout this script
# marked USER:

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
export TURBINE_ENGINES=1
export ADLB_SERVERS=1

echo "Turbine: aprun.sh"
date "+%m/%d/%Y %I:%M%p"
echo

set -x
# USER: Set Turbine installation path
export TURBINE_HOME=/lustre/beagle/wozniak/sfw/turbine-0.0.2
# USER: Select program name
PROGRAM=${TURBINE_HOME}/test/adlb-data.tcl

source ${TURBINE_HOME}/scripts/turbine-config.sh
if [[ ${?} != 0 ]]
then
  echo "turbine: configuration error!"
  exit 1
fi

# Send environment variables to PBS job:
#PBS -v TURBINE_ENGINES ADLB_SERVERS TURBINE_HOME
# USER: Set aprun parameters to agree with PBS -l settings
aprun -n 3 -N 1 -cc none -d 1 ${TCLSH} ${PROGRAM}

