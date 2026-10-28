
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
  # The build-time USE_JAVA is in the Conda build environment, which
  # may be absent or not runnable at run time, so look for Java at
  # run time.  Use the first executable candidate, in this order:
  #   1. $JAVA_HOME/bin/java
  #   2. $CONDA_PREFIX/bin/java, then $CONDA_PREFIX/lib/jvm/bin/java
  #   3. java on $PATH (any source, not only Conda)
  #   4. base env from CONDA_EXE, same two layouts
  #   5. openjdk-* directories in the base env package cache
  # Layout differs between openjdk packages, so check both.
  # Otherwise keep the build-time path; lookup_java reports it.
  JVM_CANDIDATES=()
  if [[ ${JAVA_HOME:-} != "" ]]
  then
    JVM_CANDIDATES+=( ${JAVA_HOME}/bin/java )
  fi
  if [[ ${CONDA_PREFIX:-} != "" ]]
  then
    JVM_CANDIDATES+=( ${CONDA_PREFIX}/bin/java
                      ${CONDA_PREFIX}/lib/jvm/bin/java )
  fi
  PATH_JAVA=$( command -v java 2> /dev/null )
  if [[ ${PATH_JAVA} != "" ]]
  then
    JVM_CANDIDATES+=( ${PATH_JAVA} )
  fi
  if (( ${#CONDA_EXE:-} > 0 ))
  then
    # Get the base env from parent-parent of CONDA_EXE
    CONDA_ROOT=${CONDA_EXE:h:h}
    JVM_CANDIDATES+=( ${CONDA_ROOT}/bin/java
                      ${CONDA_ROOT}/lib/jvm/bin/java
                      ${CONDA_ROOT}/pkgs/openjdk-*/bin/java(N)
                      ${CONDA_ROOT}/pkgs/openjdk-*/lib/jvm/bin/java(N) )
  fi
  CONDA_JVM=""
  for JVM_CANDIDATE in ${JVM_CANDIDATES[@]}
  do
    if [[ -x ${JVM_CANDIDATE} ]]
    then
      CONDA_JVM=${JVM_CANDIDATE}
      break
    fi
  done
  if [[ ${CONDA_JVM} != "" ]]
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
