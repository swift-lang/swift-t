#!/bin/sh

TURBINE=$( cd $(dirname $0) ; cd .. ; /bin/pwd )

source ${TURBINE}/scripts/turbine-config.sh

${TCLSH} ${TURBINE}/test/diamond.tcl
