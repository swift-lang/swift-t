#!/bin/bash
set -eu

# RUN
# Runs all examples

THIS=$( readlink --canonicalize $( dirname $0 ) )
cd $THIS

source setup.sh

# Sanity check variables:
TCL_INCLUDE=${TCL_INCLUDE_SPEC:2}
if [[ ! -f $TCL_INCLUDE/tcl.h ]]
then
  echo "Variable TCL_INCLUDE_SPEC is wrong!"
  echo "Currently: '$TCL_INCLUDE_SPEC'"
  exit 1
fi
if [[ ! -f ${BLAS} ]]
then
  echo "Variable BLAS is wrong!"
  echo "Currently: '$BLAS'"
  exit 1
fi

for T in {1..9}
do
  echo "Example $T:"
  (
    cd ${T}
    ./run.sh
  )
  echo
done

echo "$0: OK"
