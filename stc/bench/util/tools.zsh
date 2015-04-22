
# Common benchmarking tools

TURBINE=$( which turbine )
if [[ ${TURBINE} == "" ]]
then
  print "Could not find turbine!"
  return 1
fi

TURBINE_HOME=$( dirname $( dirname ${TURBINE} ) )
TURBINE_COBALT=${TURBINE_HOME}/scripts/submit/bgp/turbine-cobalt.zsh


