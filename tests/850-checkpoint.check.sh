#!/bin/bash

F_COUNT=`grep -F 'f executed' ${TURBINE_OUTPUT} | wc -l`
F_EXP=103
G_COUNT=`grep -F 'g executed' ${TURBINE_OUTPUT} | wc -l`
G_EXP=101
H_COUNT=`grep -F 'h executed' ${TURBINE_OUTPUT} | wc -l`
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
