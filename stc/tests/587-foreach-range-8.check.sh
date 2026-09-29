#!/bin/bash

# Strip carriage returns: some MPI launchers forward rank stdout over a
# pty, turning each "\n" into "\r\n", which defeats the "$" anchor below.
COUNT=$( tr -d '\r' < "${TURBINE_OUTPUT}" | \
         grep -c -E '(\[[0-9]*\])? trace: [0-9]+$' )
if [ ${COUNT} -ne 100 ]; then
    echo "Expected 100 trace statements in ${TURBINE_OUTPUT}, but only saw ${COUNT}"
    exit 1
fi
exit 0
