changecom(`dnl')#!/bin/sh
# We use changecom to change the M4 comment to dnl, not hash

# TURBINE.PBS.M4
# Turbine PBS template.  This is automatically filled in 
# by M4 in setup-turbine-pbs

#PBS -N turbine
#PBS -l nodes=1:ppn=4
#PBS -l walltime=esyscmd(`printf $WALLTIME')
#PBS -j oe

set -x

cd $PBS_O_WORKDIR

TURBINE_HOME=esyscmd(`printf $TURBINE_HOME')
PROGRAM=esyscmd(`printf $PROGRAM')

source ${TURBINE_HOME}/scripts/turbine-config.sh

mpiexec ${TCLSH} ${PROGRAM} 
