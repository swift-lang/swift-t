#!/usr/bin/env zsh

# Reference measurement

TURBINE=$( which turbine )
if (( ${?} ))
then
  print "turbine not found!"
  return 1
fi

TURBINE_HOME=$( cd $( dirname ${TURBINE} )/.. ; /bin/pwd )
source ${TURBINE_HOME}/scripts/helpers.zsh
BENCH_UTIL=$( cd $( dirname $0 )/../../util ; /bin/pwd )
source ${BENCH_UTIL}/tools.zsh
exitcode

declare -F 3 START STOP DURATION

N=1000

START=$(nanos)
for (( i=0 ; i<$N ; i++ ))
do
  touch "tmp-${i}.out"
done
STOP=$(nanos)

DURATION=$((STOP-START))

declare DURATION
