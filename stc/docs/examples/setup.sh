
# EXAMPLES SETUP.SH
# Source this before running examples

# Set these for your system:

# Get Turbine's Tcl location
TURBINE_HOME=$( cd $( dirname $( which turbine ) )/.. ; /bin/pwd )
source ${TURBINE_HOME}/scripts/turbine-build-config.sh
export TCL_INCLUDE_SPEC
export BLAS=${BLAS:-${HOME}/Downloads/BLAS/BLAS.a}
