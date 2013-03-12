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

tcl=$TMPDIR/${swift%.swift}.tcl
out=$TMPDIR/${swift%.swift}.O$opt.out
ic=$TMPDIR/${swift%.swift}.O$opt.ic
time=${swift%.swift}.O$opt.time
counts=${swift%.swift}.O$opt.counts

STC_FLAGS=
if [[ ! -z "$REFCOUNT" && "$REFCOUNT" -ne 0 ]]; then
  STC_FLAGS+=-Trefcounting
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
  turbine -n8 $tcl "$@" | $scriptdir/opcounts.py > $counts
  rc=$?
  cat $counts
fi

if [ "$?" -ne 0 ]; then
  echo "Script run failed"
  exit 1
fi
