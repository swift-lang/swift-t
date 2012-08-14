#!/usr/bin/env zsh

# Find working Tcl 8.5
# Currently used by configure

DIR=$1

source scripts/helpers.zsh
if [[ ${?} != 0 ]]
then
  print "\n Could not source helpers.zsh!"
  exit 1
fi

[[ -n ${DIR} ]]
exitcode "\n Not given: Tcl directory" > /dev/stderr

[[ -d ${DIR} ]]
exitcode "\n Could not find Tcl directory: ${DIR}" > /dev/stderr

FILES=( ${DIR}/bin/tclsh8.5 ${DIR}/bin/tclsh )
for F in ${FILES}
do
  if [[ -x ${F} ]]
  then
    if [[ ${NO_RUN} == 1 ]]
    then
      # Skip trying to run: we are cross-compiling
      VERSION=8.5
    else
      # Run and get Tcl version
      VERSION=$( print 'puts $tcl_version' | ${F} )
      exitcode "\n Could not run: ${F}"
    fi
    if [[ ${VERSION} == "8.5" ]]
      then
      print ${F}
      return 0
    fi
  fi
done

print "Could not find Tcl 8.5 binary in: ${DIR}" > /dev/stderr
return 1
