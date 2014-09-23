#!/bin/bash -eu

for T in {1..9}
do
  echo "Example ${T}:"
  ( 
    cd ${T}
    ./run.sh
  )
  echo
done
