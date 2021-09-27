
# EXAMPLES SETUP.SH
# Source this before running examples

which swift-t turbine

# Set these for your system:

# Get Turbine's build settings
source $( turbine -C )
export TCL_INCLUDE_SPEC TCL_HOME

# Set BLAS here
# (The Jenkins test script (jenkins.zsh) sets BLAS)
export BLAS=${BLAS:-${HOME}/Downloads/BLAS-3.6.0/BLAS.a}
