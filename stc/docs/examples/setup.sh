
# EXAMPLES SETUP.SH
# Source this before running examples

# Set these for your system:

# Get Turbine's build settings
source $( turbine -S )
export TCL_INCLUDE_SPEC TCL_HOME

export BLAS=${BLAS:-${HOME}/Downloads/BLAS/BLAS.a}
