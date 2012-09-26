#!/bin/zsh

# Turbine Output Search
# for JOBID

JOBID=$1

DIRS=$( lsd_leaf ~/turbine-output )

for D in ${DIRS}
do
  if [[ -f ${D}/jobid.txt ]]
  then
    if [[ $( < ${D}/jobid.txt ) == ${JOBID} ]]
    then
      print ${D}
      exit 0
    fi
  fi
done

exit 1
