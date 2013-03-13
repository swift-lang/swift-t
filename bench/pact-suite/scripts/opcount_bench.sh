#!/usr/bin/env bash

opt=$1
shift

mode=$1
shift

if [[ $mode != TIME && $mode != OPCOUNT ]]; then
  echo "Invalid mode $mode"
  exit 1
fi

swift=$1
shift

TMPDIR=/var/tmp/pact-bench
mkdir -p $TMPDIR

if [ ! -d $TMPDIR ] ; then
  echo $TMPDIR could not be created
  exit 1
fi

benchprefix=$(basename ${swift%.swift})
optstring=$(echo "$@" | sed -e 's/ --/__/g' -e 's/[-= /]/_/g')
benchname="${benchprefix}.${optstring}.O${opt}"
echo "Benchmark name $benchname"


tcl=$TMPDIR/$benchname.tcl
out=$TMPDIR/$benchname.out
ic=$TMPDIR/$benchname.ic
time=$benchname.time
counts=$benchname.counts

STC_FLAGS=
if [[ ! -z "$REFCOUNT" && "$REFCOUNT" -ne 0 ]]; then
  STC_FLAGS+=-Trefcounting
fi
if [[ $benchprefix == annealing-exm ]]; then
  STC_FLAGS+=" -I /home/tga/ExM/scicolsim.git/src"
  export TURBINE_USER_LIB="/home/tga/ExM/scicolsim.git/lib"
fi
stc -O$opt -C$ic $STC_FLAGS $swift $tcl
rc=$?
if [ "$?" -ne 0 ]; then
  echo "Compile failed"
  exit 1
fi

scriptdir=`dirname $0`
if [[ $MODE == OPCOUNT ]]; then
  $out > $counts
fi

LAUNCH=
if [[ $mode == TIME ]]; then
  export DEBUG=0
  export TURBINE_LOG=0
  export TURBINE_DEBUG=0
  export ADLB_DEBUG=0
  /usr/bin/time -o $time turbine -n8 $tcl "$@"
  rc=$?
  cat $time
else
  time turbine -n3 $tcl "$@" | $scriptdir/opcounts.py > $counts
  rc=$?
  cat $counts
fi

if [ "$?" -ne 0 ]; then
  echo "Script run failed"
  exit 1
fi
