#!/usr/bin/env zsh

# Find working Tcl in directory DIR ($1)
# Refers to TCL_VERSION in the environment
# Currently used by configure

DIR=$1

source scripts/helpers.zsh
if [[ ${?} != 0 ]]
then
  print "\n Could not source helpers.zsh!" > /dev/stderr
  exit 1
fi

[[ -n ${DIR} ]]
exitcode "\n Not given: Tcl directory" > /dev/stderr

[[ -d ${DIR} ]]
exitcode "\n Could not find Tcl directory: ${DIR}" > /dev/stderr

if [[ ${TCL_VERSION} == "" ]] 
then 
  print "Not set: TCL_VERSION" > /dev/stderr
  exit 1
fi
  
# Loop over F: the tclsh executable file
FILES=( ${DIR}/bin/tclsh${TCL_VERSION} ${DIR}/bin/tclsh )
for F in ${FILES}
do
  if [[ -x ${F} ]]
  then
    # Get V: the version reported by tclsh
    if [[ ${NO_RUN} == 1 ]]
    then
      # Skip trying to run: we are cross-compiling
      V=${TCL_VERSION}
    else
      # Run and get Tcl version
      V=$( print 'puts $tcl_version' | ${F} )
      exitcode "\n Could not run: ${F}"
    fi
    if [[ ${V} == ${TCL_VERSION} ]]
    then
      # This works: return success
      print ${F}
      return 0
    fi
  fi
done

# If we get here, we did not find an executable
print "Could not find Tcl ${TCL_VERSION} binary in: ${DIR}" \
      > /dev/stderr
return 1
