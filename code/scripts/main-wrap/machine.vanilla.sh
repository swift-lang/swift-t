
# VANILLA

TCL_VERSION=${TCL_VERSION:-8.5}
TCLSH=$( which tclsh$TCL_VERSION ; true )
[[ $TCLSH == "" ]] && crash "machine.vanilla.sh: Could not find tclsh$TCL_VERSION"
TCL_HOME=$(cd $(dirname $TCLSH)/.. ; /bin/pwd)

TURBINE=$( which turbine ; true )
[[ $TURBINE == "" ]] && crash "machine.vanilla.sh: Could not find turbine"
TURBINE_HOME=$(cd $(dirname $TURBINE)/.. ; /bin/pwd)
ADLB_HOME=$(cd $TURBINE_HOME/../lb 2>/dev/null || cd $TURBINE_HOME/../adlb 2>/dev/null; /bin/pwd)
C_UTILS_HOME=$(cd $TURBINE_HOME/../c-utils ; /bin/pwd)

MPIEXEC=$( which mpiexec )
[[ $MPIEXEC == "" ]] && crash "machine.vanilla.sh: Could not find mpiexec"
MPI_HOME=$(cd $(dirname $MPIEXEC)/.. ; /bin/pwd)
MPICC=$( which mpicc ; true )
[[ $MPICC == "" ]] && crash "machine.vanilla.sh: Could not find mpicc"

CC=gcc
STC=stc

source $GENLEAF_HOME/flags.gcc.sh
