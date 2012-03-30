
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
