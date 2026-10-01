COASTER_SVC=coaster-service
SVC_CONF="$(dirname $0)/695-app-coasters.coaster.conf"

#TODO: assumes coaster-service on path
if ! which $COASTER_SVC &> /dev/null ; then
  echo "${COASTER_SVC} not on path"
  exit 1
fi

source "${SVC_CONF}"
"${COASTER_SVC}" -nosec -port ${SERVICE_PORT} &
COASTER_SVC_PID=$!

# Delay to allow service to start up
sleep 0.5

COASTER_SERVICE_URL="${IPADDR}:${SERVICE_PORT}"
export TURBINE_COASTER_CONFIG="jobManager=local,maxParallelTasks=64"
TURBINE_COASTER_CONFIG+=",coasterServiceURL=${COASTER_SERVICE_URL}"

# Need at least one worker
export TURBINE_COASTER_WORKERS=1

# Need at least two workers for additional work types
if [[ -z "${TEST_ADLB_WORKERS-}" || "$TEST_ADLB_WORKERS" -lt 2 ]] ; then
  export TEST_ADLB_WORKERS=2
fi

# We request more ranks than a small CI runner has cores.  Open MPI
# refuses to oversubscribe by default ("not enough slots available");
# allow it.  These are Open MPI variables, ignored by other MPIs.
export OMPI_MCA_rmaps_base_oversubscribe=yes                  # Open MPI 4
export PRTE_MCA_rmaps_default_mapping_policy=:oversubscribe   # Open MPI 5
