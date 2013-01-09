#!/bin/zsh

# SETUP TURBINE PBS
# Filters the Turbine PBS template turbine.pbs.m4
# This is done by simply using environment variables 
# in M4 to create the Turbine PBS submit file (turbine.pbs)

# USAGE
# > VAR1=VALUE1 VAR2=VALUE2 setup-turbine-pbs <output>?
# If output is not specified, uses ./turbine.pbs
# Required variables are: 
#    PROGRAM: The Turbine program to run
# Recognized variables are: 
#    WALLTIME: The PBS walltime as HH:MM:SS (default 15min)
# Then, run qsub <options> turbine.pbs

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
declare PROGRAM

TURBINE_PBS_M4=${TURBINE_HOME}/scripts/submit/pbs/turbine.pbs.m4
TURBINE_PBS=${1:-turbine.pbs}
export WALLTIME=${WALLTIME:-00:15:00}

touch ${TURBINE_PBS}
exitcode "Could not write to: ${TURBINE_PBS}"

m4 ${TURBINE_PBS_M4} > ${TURBINE_PBS}
exitcode "Errors in M4 processing!"

print "wrote: ${TURBINE_PBS}"
