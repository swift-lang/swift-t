#!/bin/zsh

TURBINE=$( which turbine ) 
if [[ ${?} != 0 ]] 
then
  print "Could not find Turbine in PATH!"
  return 1
fi

export TURBINE_HOME=$( cd $(dirname ${TURBINE})/.. ; /bin/pwd )

declare TURBINE_HOME

source ${TURBINE_HOME}/scripts/helpers.zsh
if [[ ${?} != 0 ]] 
then
  print "Broken Turbine installation!"
  declare TURBINE_HOME
  return 1
fi

checkvars PROGRAM

TURBINE_PBS_M4=${TURBINE_HOME}/scripts/submit/pbs/turbine.pbs.m4
TURBINE_PBS=${1:-turbine.pbs}
export WALLTIME=${WALLTIME:-00:15:00}

touch ${TURBINE_PBS}
exitcode "Could not write to: ${TURBINE_PBS}"

m4 < ${TURBINE_PBS_M4} > ${TURBINE_PBS}
exitcode "Errors in M4 processing!"

print "wrote: ${TURBINE_PBS}"
