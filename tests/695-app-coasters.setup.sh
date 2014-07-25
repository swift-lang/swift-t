#!/bin/bash

# Assume coaster service in path
stop-coaster-service -conf 695-app-coasters.coaster.conf
start-coaster-service -conf 695-app-coasters.coaster.conf

export COASTER_SERVICE_URL="127.0.0.1:53321"

# Need at least one worker
export TURBINE_COASTER_WORKERS=1
