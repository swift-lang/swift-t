#!/bin/bash

# No end-of-line anchor: some MPI launchers forward rank stdout over a
# pty, turning each "\n" into "\r\n", and a trailing "$" would then match
# nothing.
COUNT=$( grep -c -E 'trace: [0-9]+' "${TURBINE_OUTFILE}" )
if [ ${COUNT} -ne 100 ]; then
    echo "Expected 100 trace, saw ${COUNT} : in ${TURBINE_OUTFILE}"
    exit 1
fi
exit 0
