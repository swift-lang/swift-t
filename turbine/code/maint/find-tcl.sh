#!/bin/sh
set -eu

# FIND-TCL

# Find working Tcl in directory DIR ($1)
# Refers to TCL_VERSION in the environment
# Currently used by configure
# This should support the minimal POSIX shell

# Copied from /dev/helpers.sh: Cannot source that file (#111)
crash()
{
  echo    > /dev/stderr
  echo $1 > /dev/stderr
  exit 1
}

if [ ${#} = 0 ]
then
  crash "Not given: Tcl directory"
fi
DIR=$1
NO_RUN=${NO_RUN:-0}

THIS=$( dirname $0 )
SWIFT_T=$( cd $THIS/../../.. ; /bin/pwd )

if ! [ -d $DIR ]
then
  crash "Could not find Tcl directory: ${DIR}"
fi

if [ -z "${TCL_VERSION:-}" ]
then
  crash "Not set: TCL_VERSION"
fi

# Loop over F: the possible tclsh executable files
FILES="$DIR/bin/tclsh$TCL_VERSION $DIR/bin/tclsh"
for F in $FILES
do
  if [ -x $F ]
  then
    # Get V: the version reported by tclsh
    if [ ${NO_RUN} = 1 ]
    then
      # Skip trying to run: we are cross-compiling
      V=$TCL_VERSION
    else
      # Run and get Tcl version
      if ! V=$( echo 'puts $tcl_version' | $F )
      then
        crash "Could not run: $F"
      fi
    fi
    if [ $V = $TCL_VERSION ]
    then
      # This works: return success
      echo $F
      exit 0
    fi
  fi
done

# If we get here, we did not find an executable
crash "Could not find Tcl ${TCL_VERSION} binary in: ${DIR}"
