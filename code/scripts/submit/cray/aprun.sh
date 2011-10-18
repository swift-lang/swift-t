#!/bin/sh

# Sample submit script for Cray systems
# https://sites.google.com/site/exmproject/development/turbine---build#TOC-Cray-

#PBS -N turbine
#PBS -l mppwidth=3,mppnppn=1,mppdepth=1
#PBS -l walltime=10:00
#PBS -o /home/users/p01226/pbs.out
#PBS -j oe
#PBS -m n

# Set number of Turbine processes
export TURBINE_ENGINES=1
export ADLB_SERVERS=1

echo "Turbine: aprun.sh"
date "+%m/%d/%Y %I:%M%p"
echo

set -x
# Set Turbine location
export TURBINE=${PBS_O_HOME}/import/turbine
# Select test name
TEST=${TURBINE}/test/adlb-data.tcl

# Send environment variables to PBS job:
#PBS -v TURBINE_ENGINES ADLB_SERVERS TURBINE
aprun -n 3 -N 1 -cc none -d 1 ${TURBINE}/bin/turbine ${TEST}
