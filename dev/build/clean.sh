#!/bin/sh
set -eu

# CLEAN SH
# Do a make clean or ant clean everywhere

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

