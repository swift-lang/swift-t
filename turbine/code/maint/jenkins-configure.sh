
# Jenkins - important variables
# Should be sourced by jenkins.sh and jenkins-tests.zsh

MPICH=/tmp/mpich-install
C_UTILS=/tmp/exm-install/c-utils
ADLB=/tmp/exm-install/lb
TURBINE=/tmp/exm-install/turbine
PATH=${PATH}:$MPICH/bin:$TURBINE/bin
