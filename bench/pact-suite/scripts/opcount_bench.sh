#!/usr/bin/env bash

swift=$1
shift

opt=$1
shift

tcl=${swift%.swift}.tcl
out=${swift%.swift}.out
ic=${swift%.swift}.ic

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

turbine -n8 $tcl "$@" > $out
rc=$?
if [ "$?" -ne 0 ]; then
  echo "Script run failed, output in $out"
  exit 1
fi

if grep -q 'WAITING TRANSFORMS: ' $out; then
  echo "Script run failed, waiting transforms, output in $out"
  exit 1
fi

scriptdir=`dirname $0`

$scriptdir/opcounts.py $out
