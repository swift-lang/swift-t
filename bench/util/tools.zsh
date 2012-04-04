
# Common benchmarking tools

compile()
{
  SWIFT=$1
  TCL=$2
  shift 2

  if [[ ! -f ${TCL} || ${SWIFT} -nt ${TCL} ]]
  then
    stc ${*} ${SWIFT} ${TCL}
    return ${?}
  fi
  return 0
}

TURBINE=$( which turbine )
if [[ ${TURBINE} == "" ]]
then
  print "Could not find turbine!"
  return 1
fi

TURBINE_HOME=$( dirname $( dirname ${TURBINE} ) )
declare TURBINE_HOME
TURBINE_COBALT=${TURBINE_HOME}/scripts/submit/bgp/turbine-cobalt.zsh
