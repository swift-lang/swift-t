#!/bin/bash

COUNT=`grep "trace: " ${TURBINE_OUTPUT} | wc -l`
if [ ${COUNT} -ne 100 ]; then
    echo "Expected 100 trace statements in ${TURBINE_OUTPUT}, but only saw ${COUNT}"
    exit 1
fi
exit 0
