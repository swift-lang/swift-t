THISDIR=`dirname $0`
DEV_DIR=`cd ${THISDIR}; pwd`

REPO_ROOT=`cd ${DEV_DIR}/.. ; pwd`

# Install dir locations
# Modify
INST=${REPO_ROOT}/inst
TCL_INST=${INST}/tcl
MPICH_INST=${INST}/mpich2
MPE_INST=${INST}/mpe
LB_INST=${INST}/lb
C_UTILS_INST=${INST}/c-utils
TURBINE_INST=${INST}/turbine

# Source locations
# Modify to match your layout if you do not have full svn repo checked out
SFW_ROOT=${REPO_ROOT}
C_UTILS=${SFW_ROOT}/c-utils
LB=${SFW_ROOT}/lb/code
TURBINE=${SFW_ROOT}/turbine/code
STC=${SFW_ROOT}/stc/code

# Misc settings
MAKE_PARALLELISM=2

# Build options: uncomment to enable

# Skip running autotools (assume configure scripts present)
SKIP_AUTOTOOLS=1

# Optimized build
#EXM_OPT_BUILD=1

# Debug build (extra logging)
#EXM_DEBUG_BUILD=1

# ENable MPE
#ENABLE_MPE=1

# Cray systems
#EXM_CRAY=1
