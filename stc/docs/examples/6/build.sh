#!/bin/sh
set -eu

# Editable settings:
TCL=${HOME}/sfw/tcl-8.6.0
TCL_CONFIG=${TCL}/lib/tclConfig.sh

. $TCL_CONFIG
export TCL_INCLUDE_SPEC
make
