changecom(`dnl')#!/bin/sh

#PBS -N turbine
#PBS -l nodes=1:ppn=4
#PBS -l walltime=esyscmd(`printf $WALLTIME')
#PBS -j oe

set -x

cd $PBS_O_WORKDIR

TURBINE_HOME=esyscmd(`printf $TURBINE_HOME')
PROGRAM=esyscmd(`printf $PROGRAM')

source ${TURBINE_HOME}/scripts/turbine-config.sh

mpiexec ${TCLSH} 
