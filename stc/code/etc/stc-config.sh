
# STC CONFIG

# Bash and ZSH compatible script that loads STC configuration settings into
# shell variables.
# Can be sourced by other scripts.
# The @@ substitutions are performed by filters in build.xml
#                      target "install"

# Exit codes: (cf. ExitCode.java)
EXIT_ERROR_SCRIPT=6

# These values are filtered in by build.xml
TURBINE_DEFAULT_HOME=@TURBINE_HOME@
STC_SRC=@STC_SRC@
DEBIAN_BUILD=@DEBIAN_BUILD@
USE_JAVA=@USE_JAVA@
# End build.xml variables

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

if (( ${#USE_JAVA} > 0 ))
then
  JVM=${USE_JAVA}
fi
