#!/bin/bash

# TODO: automatically start coaster service?
# Hardcode service url for now
export COASTER_SERVICE_URL="127.0.0.1:53001"

# Need at least one worker
export TURBINE_COASTER_WORKERS=1
