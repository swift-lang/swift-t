#!/bin/sh

# ACTIVATE SH

# This is called when the user activates the Anaconda environment
#      containing swift-t or swift-t-r .
# If R_LIBS_USER is unset,
#      R will add a default library location to .libPaths()
# This commonly breaks R because the Anaconda R does not match other
#      R installations on the same system.
# Here, we force R_LIBS_USER to the Anaconda R library location.
# This may make it more difficult for users to reuse libraries on the
#      same system, but that is a small price to pay against very
#      confusing error messages due to compiler incompatibilies.

if [[ ! -z ${R_LIBS_USER+x} ]]; then
  export R_LIBS_USER_BACKUP="$R_LIBS_USER"
fi
export R_LIBS_USER="${CONDA_PREFIX}/lib/R/library"
