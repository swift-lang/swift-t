#!/bin/bash

# Initial script to view data assignments from Turbine log

LOG=$1

if [[ ${LOG} == "" ]]
then
  echo "Not given: LOG"
  exit 1
fi

grep " integer:\| container:\|function:\|insert:\|store:\|trace:" ${LOG}
