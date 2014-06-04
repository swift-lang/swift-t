
# MIRA GCC

#TCL_HOME=/home/wozniak/Public/sfw/ppc64/tcl-8.5.12
TCL_HOME=/home/wozniak/Public/sfw/ppc64/bgxlc/tcl-8.5.12
TCL_VERSION=8.5

CC=/bgsys/drivers/ppcfloor/gnu-linux/bin/powerpc64-bgq-linux-gcc
STC=/home/wozniak/Public/sfw/ppc64/stc/bin/stc

MPI_HOME=/bgsys/drivers/V1R2M1/ppc64/comm
MPI_LIB_NAME=mpich-gcc
MPICC=/bgsys/drivers/V1R2M1/ppc64/comm/bin/gcc/mpicc

TURBINE_HOME=/home/wozniak/Public/sfw/ppc64/turbine
ADLB_HOME=/home/wozniak/Public/sfw/ppc64/lb
C_UTILS_HOME=/home/wozniak/Public/sfw/ppc64/c-utils

source $GENLEAF_HOME/flags.gcc.sh
