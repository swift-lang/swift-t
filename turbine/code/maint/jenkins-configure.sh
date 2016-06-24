
# Jenkins - configure and generate Makefile
# Should be sourced by jenkins.sh

MPICH=/tmp/mpich-install
C_UTILS=/tmp/exm-install/c-utils
ADLB=/tmp/exm-install/lb
TURBINE=/tmp/exm-install/turbine
PATH=${PATH}:$MPICH/bin:$TURBINE/bin
