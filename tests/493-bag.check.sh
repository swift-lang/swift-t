#!/bin/sh

if grep -q FAILURE "${TURBINE_OUTPUT}"; then
    echo "FAILED!"
    exit 1
fi

if grep -q -F 'F1,string1' "${TURBINE_OUTPUT}"; then
    :
else
    echo "expected F1 to have string1: FAILED!"
    exit 1
fi

if grep -q -F 'F2,string2' "${TURBINE_OUTPUT}"; then
    :
else
    echo "expected F2 to have string2: FAILED!"
    exit 1
fi

# TODO: check BEFORE THEN AFTER
