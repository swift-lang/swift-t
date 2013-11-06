#!/bin/bash

ARRAYF_COUNT=`grep -F 'trace: arrayf executed' ${TURBINE_OUTPUT} | wc -l`
ARRAYF_R_COUNT=`grep -F 'trace: arrayf executed' ${TURBINE_XPT_RELOAD_OUTPUT} | wc -l`
ARRAYF_EXP=1

if [ ${ARRAYF_COUNT} -ne ${ARRAYF_EXP} ]; then
    echo "Expected arrayf to execute ${ARRAYF_EXP} times in ${TURBINE_OUTPUT}, but saw ${ARRAYF_COUNT}"
    exit 1
fi

if [ ${ARRAYF_R_COUNT} -ne 0 ]; then
    echo "Reran arrayf ${ARRAYF_R_COUNT}: should have been restored from checkpoint"
    exit 1
fi
