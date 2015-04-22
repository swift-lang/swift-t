#!/bin/bash

F_COUNT=`grep -F 'trace: f executed' ${TURBINE_OUTPUT} | wc -l`
F_R_COUNT=`grep -F 'trace: f executed' ${TURBINE_XPT_RELOAD_OUTPUT} | wc -l`
F_EXP=103
G_COUNT=`grep -F 'trace: g executed' ${TURBINE_OUTPUT} | wc -l`
G_R_COUNT=`grep -F 'trace: g executed' ${TURBINE_XPT_RELOAD_OUTPUT} | wc -l`
G_EXP=101
H_COUNT=`grep -F 'trace: h executed' ${TURBINE_OUTPUT} | wc -l`
H_R_COUNT=`grep -F 'trace: h executed' ${TURBINE_XPT_RELOAD_OUTPUT} | wc -l`
G_EXP=101
H_EXP=101

if [ ${F_COUNT} -ne ${F_EXP} ]; then
    echo "Expected f to execute ${F_EXP} times in ${TURBINE_OUTPUT}, but saw ${F_COUNT}"
    exit 1
fi

if [ ${G_COUNT} -ne ${G_EXP} ]; then
    echo "Expected g to execute ${G_EXP} times in ${TURBINE_OUTPUT}, but saw ${G_COUNT}"
    exit 1
fi

if [ ${H_COUNT} -ne ${H_EXP} ]; then
    echo "Expected f to execute ${H_EXP} times in ${TURBINE_OUTPUT}, but saw ${H_COUNT}"
    exit 1
fi

if [ ${F_R_COUNT} -ne 0 ]; then
    echo "Reran f ${F_R_COUNT}: should have been restored from checkpoint"
    exit 1
fi

if [ ${G_R_COUNT} -ne 0 ]; then
    echo "Reran f ${G_R_COUNT}: should have been restored from checkpoint"
    exit 1
fi

if [ ${H_R_COUNT} -ne 0 ]; then
    echo "Reran f ${H_R_COUNT}: should have been restored from checkpoint"
    exit 1
fi
