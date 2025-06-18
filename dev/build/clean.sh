#!/bin/sh
set -eu

# CLEAN SH
# Do a make clean or ant clean everywhere

if [ ${#} > 0 ]
then
  echo "clean.sh: Provide no arguments!"
  exit 1
fi

for D in c-utils lb turbine
do
  cd $D/code
  if [ -f Makefile ]
  then
    make clean
  fi
  cd ../..
done

cd stc/code
ant clean
