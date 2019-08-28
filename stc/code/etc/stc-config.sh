
# STC CONFIG

# Bash and ZSH compatible script that loads STC configuration settings into
# shell variables.
# Can be sourced by other scripts.
# This will abort the process if any errors are encountered.
# This is filtered by build.xml target "install"

# Exit codes: (cf. ExitCode.java)
EXIT_ERROR_SCRIPT=6

# Find Turbine (for include path).  Order of priority is:
# 1. User-set TURBINE_HOME environment variable
# 2. TURBINE_DEFAULT_HOME - the build-time setting
TURBINE_DEFAULT_HOME=@TURBINE_HOME@ # Filled in by build.xml
TURBINE_HOME=${TURBINE_HOME:-${TURBINE_DEFAULT_HOME}}

DEBIAN_BUILD=@DEBIAN_BUILD@ # Filled in by build.xml
if (( DEBIAN_BUILD ))
then
  TURBINE_TOP=@TURBINE_HOME@/lib/turbine
else
  TURBINE_TOP=@TURBINE_HOME@
fi

if [[ ! -x "${TURBINE_HOME}/bin/turbine" ]]
then
  print "Invalid Turbine installation: ${TURBINE_HOME}"
  print "Turbine is not executable: ${TURBINE_HOME}/bin/turbine"
  exit ${EXIT_ERROR_SCRIPT}
fi

USE_JAVA=@USE_JAVA@
if (( ${#USE_JAVA} > 0 ))
then
  JVM=${USE_JAVA}
fi
