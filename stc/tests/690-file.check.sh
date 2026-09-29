#!/bin/bash

# Look for filename output at end of line.
# Strip carriage returns: some MPI launchers forward rank stdout over a
# pty, turning each "\n" into "\r\n", which defeats the "$" anchor.
tr -d '\r' < "${TURBINE_OUTFILE}" | grep -q "alice.txt$"
CODE=$?

rm -f alice.txt
exit ${CODE}
