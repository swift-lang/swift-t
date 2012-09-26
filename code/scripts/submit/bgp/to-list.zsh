#!/bin/zsh

# Turbine Output Search
# COUNT most recent runs

COUNT=$1

checkvars COUNT

DIRS=$( lsd_leaf ~/turbine-output | tail -${COUNT} )

for D in ${DIRS}
do
  print $( < ${D}/jobid.txt ) ${D}
done

exit 1
