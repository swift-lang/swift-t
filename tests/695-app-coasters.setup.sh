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

export COASTER_SERVICE_URL="${IPADDR}:${SERVICE_PORT}"
export TURBINE_COASTER_CONFIG="jobManager=local,maxParallelTasks=64"

# Need at least one worker
export TURBINE_COASTER_WORKERS=1
