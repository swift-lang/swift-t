
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
  # The build-time USE_JAVA points into the Conda build environment,
  # which is not where stc runs: it is absent in a normal install, and
  # under 'conda build --dirty' it still exists on disk but its Java is
  # not runnable in the install/test env.  So under Conda always resolve
  # Java from the *active* environment, trying, in order:
  #   1. $CONDA_PREFIX/bin/java - the activated env (install and test)
  #   2. java on $PATH          - the activated env is on PATH
  #   3. the base env via CONDA_EXE, including its openjdk package cache
  # Only if all of these fail do we keep the build-time path (which
  # lookup_java will then report as unusable).
  # (Previously this used only the CONDA_EXE-derived base prefix and was
  #  gated on the baked path being missing, which --dirty defeated.)
  CONDA_JVM=""
  if [[ -n ${CONDA_PREFIX:-} && -x ${CONDA_PREFIX}/bin/java ]]
  then
    CONDA_JVM=${CONDA_PREFIX}/bin/java
  else
    PATH_JAVA=$( command -v java 2> /dev/null )
    if [[ -n ${PATH_JAVA} ]]
    then
      CONDA_JVM=${PATH_JAVA}
    elif (( ${#CONDA_EXE:-} > 0 ))
    then
      # Get the base env from parent-parent of CONDA_EXE
      CONDA_ROOT=${CONDA_EXE:h:h}
      if [[ -x ${CONDA_ROOT}/bin/java ]]
      then
        CONDA_JVM=${CONDA_ROOT}/bin/java
      else
        JDKS=( ${CONDA_ROOT}/pkgs/openjdk-*(N) )
        if (( ${#JDKS} ))
        then
          CONDA_JVM=${JDKS[1]}/bin/java
        fi
      fi
    fi
  fi
  if [[ -n ${CONDA_JVM} ]]
  then
    JVM=${CONDA_JVM}
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
