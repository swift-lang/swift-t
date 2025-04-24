
# STC CONFIG

# Bash and ZSH compatible script that loads STC configuration settings
# into shell variables.
# Can be sourced by other scripts.
# The @@ substitutions are performed by filters in build.xml
#     target "install" when the files are copied into the installation

# JAVA_HOME: In a Conda-based installation on OSX, conda activate
#            sets JAVA_HOME, which makes the /usr/bin/java
#            stub work.  So you must activate the environment
#            to get STC to work, or you will get
#            "Unable to locate a Java Runtime."

# TIMESTAMP: @TIMESTAMP@

# Exit codes: (cf. ExitCode.java)
EXIT_ERROR_SCRIPT=6

TURBINE_DEFAULT_HOME=@TURBINE_HOME@
STC_SRC=@STC_SRC@
DEBIAN_BUILD=@DEBIAN_BUILD@  # 0 or 1
# Build-time JVM.  This may change at run time under Anaconda
#                  This may be overridden by 'stc -j'
USE_JAVA=@USE_JAVA@
CONDA_BUILD=@CONDA_BUILD@  # 0 or 1
# End build.xml variables

if (( ${#USE_JAVA} > 0 ))
then
  JVM=${USE_JAVA}
fi
if (( CONDA_BUILD ))
then
  if [[ ! -f ${JVM:-0} ]]
  then
    # Get CONDA_PREFIX from parent-parent of CONDA_EXE
    if (( ${#CONDA_EXE:-} == 0 )) {
      echo "stc: CONDA_EXE is not set"
      echo "stc: The conda environment is broken!"
      exit ${EXIT_ERROR_SCRIPT}
    }
    CONDA_PREFIX=${CONDA_EXE:h:h}
    if [[ -x ${CONDA_PREFIX}/bin/java ]]
    then
      JVM=${CONDA_PREFIX}/bin/java
    else
      JDKS=( ${CONDA_PREFIX}/pkgs/openjdk-* )
      if (( ${#JDKS} ))
      then
        JVM=${JDKS[1]}/bin/java
      fi
    fi
  fi
fi

# Find Turbine (for include path).  The order of priority is:
# 1. User-set TURBINE_HOME environment variable
# 2. TURBINE_DEFAULT_HOME - the build-time setting
TURBINE_HOME=${TURBINE_HOME:-${TURBINE_DEFAULT_HOME}}

if (( DEBIAN_BUILD ))
then
  TURBINE_TOP=${TURBINE_DEFAULT_HOME}/lib/turbine
else
  TURBINE_TOP=${TURBINE_DEFAULT_HOME}
fi

if [[ ! -x "${TURBINE_HOME}/bin/turbine" ]]
then
  print "STC: Invalid Turbine installation: ${TURBINE_HOME}"
  print "STC: Turbine is not executable: ${TURBINE_HOME}/bin/turbine"
  exit ${EXIT_ERROR_SCRIPT}
fi
