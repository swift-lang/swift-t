#!/bin/bash -eu

export TCL_INCLUDE=${HOME}/sfw/tcl-8.6.0

for T in {1..9}
do
  echo "Example ${T}:"
  (
    cd ${T}
    ./run.sh
  )
  echo
done
