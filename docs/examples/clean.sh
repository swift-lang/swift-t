#!/bin/bash

cd $( dirname $0 )

set -e

for D in {1..6}
do
  cd ${D}
  echo "clean: ${PWD}"
  ./clean.sh
  cd ..
done
