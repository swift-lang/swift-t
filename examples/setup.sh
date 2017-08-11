
# EXAMPLES SETUP.SH
# Source this before running examples

# Set these for your system:

echo PATH: $PATH
which turbine

# Get Turbine's build settings
source $( turbine -S )
export TCL_INCLUDE_SPEC TCL_HOME

# Set BLAS here
# (The Jenkins test script (jenkins.zsh) sets BLAS)
export BLAS=${BLAS:-${HOME}/Downloads/BLAS-3.6.0/BLAS.a}
