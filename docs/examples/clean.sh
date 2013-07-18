#!/bin/bash -e

cd $( dirname $0 )

for D in {1..7}
do
  cd ${D}
  echo "clean: ${PWD}"
  ./clean.sh
  cd ..
done
