#!/bin/zsh

# Find working Tcl 8.5
# Currently used by Makefile

DIR=$1

source scripts/helpers.zsh
if [[ ${?} != 0 ]]
then
  print "Could not source helpers.zsh!"
  exit 1
fi

[[ -n ${DIR} ]]
exitcode "Not given: Tcl directory" > /dev/stderr

[[ -d ${DIR} ]]
exitcode "Could not find Tcl directory: ${DIR}" > /dev/stderr

FILES=( ${DIR}/bin/tclsh8.5 ${DIR}/bin/tclsh )
for F in ${FILES}
do
  if [[ -x ${F} ]]
  then
    # Run and get Tcl version
    VERSION=$( print 'puts $tcl_version' | ${F} )
    exitcode "Could not run: ${F}"
    if [[ ${VERSION} == "8.5" ]]
    then
      print ${F}
      return 0
    fi
  fi
done

print "Could not find Tcl 8.5 binary in: ${DIR}" > /dev/stderr
return 1
