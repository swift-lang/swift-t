# bash and zsh compatible script that loads STC configuration settings into
# shell variables.  Should be sourced by other scripts.  STC_ENV must be set
# to location of stc-env.sh config file.  This will abort process if any
# errors encountered.
# Any variables in stc-env.sh are set, plus the following are always set:
# TURBINE_HOME - always set

# Exit codes: (cf. ExitCode.java)
EXIT_ERROR_SCRIPT=6

if [ -f ${STC_ENV} ]
then
  source ${STC_ENV}
else
  echo "Warning: Configuration file ${STC_ENV} does not exist."
fi

# Find Turbine (for include path).  Order of priority is:
# 1. User-set TURBINE_HOME environment variable
# 2. TURBINE_DEFAULT_HOME from stc-env.sh
if [[ -n "${TURBINE_HOME+x}" ]]
then
  # User-provided
  :
elif [[ -n "${TURBINE_DEFAULT_HOME+x}" ]]
then
  TURBINE_HOME=${TURBINE_DEFAULT_HOME}
else
  print "Not set: TURBINE_HOME or TURBINE_DEFAULT_HOME"
  exit ${EXIT_ERROR_SCRIPT}
fi

if [[ ! -x "${TURBINE_HOME}/bin/turbine" ]]
then
  print "${TURBINE_HOME} does not appear to be a valid Turbine installation: expected ${TURBINE_HOME}/bin/turbine to be present"
  exit ${EXIT_ERROR_SCRIPT}
fi

