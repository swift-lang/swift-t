#!/bin/sh
set -eu

# Editable settings:
# TCL=${HOME}/sfw/tcl-8.6.0
# Mira/Cetus:
TCL=${HOME}/Public/sfw/ppc64/bgxlc/dynamic/tcl-8.5.12
TCL_CONFIG=${TCL}/lib/tclConfig.sh
COMPILER=GNU
# COMPILER=BGQ-GNU
# COMPILER=BGQ

echo Using COMPILER=$COMPILER
. $TCL_CONFIG
export COMPILER TCL_INCLUDE_SPEC
make ${*}
