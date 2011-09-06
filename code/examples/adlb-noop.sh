#!/bin/sh

HOMEDIR=/home/wozniak

set -x 
{
  echo PMI_RANK: $PMI_RANK 
  TURBINE=${HOMEDIR}/exm/turbine
  ${TURBINE}/bin/turbine ${TURBINE}/test/adlb-noop.tcl 
} >> ${HOMEDIR}/output.txt 2>&1
