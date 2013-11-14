#!/usr/bin/env bash

opt=$1
shift

mode=$1
shift

if [[ $mode != TIME && $mode != OPCOUNT && $mode != DEBUG ]]; then
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

STC_FLAGS=${STC_FLAGS} # Can be set in env
if [[ ! -z "$NO_REFCOUNT" && "$NO_REFCOUNT" -ne 0 ]]; then
  echo "Disabling refcounting"
  STC_FLAGS+=" -trefcounting"
fi
if [[ $benchprefix == annealing-exm ]]; then
  STC_FLAGS+=" -I $HOME/ExM/scicolsim.git/src"
  export TURBINE_USER_LIB="$HOME/ExM/scicolsim.git/lib"
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

UNIQUIFIER=
if [[ ! -z "$PACT_TRIAL" ]] ; then
  UNIQUIFIER=.$PACT_TRIAL
fi

if [[ ! -z "$CPU_PROF" ]] ; then
  export LD_PRELOAD=$HOME/ExM/inst/gperftools/lib/libprofiler.so
  export CPUPROFILE=$benchname.prof$UNIQUIFIER
fi

time=$benchname.time$UNIQUIFIER
counts=$benchname.counts$UNIQUIFIER
out=$benchname.out$UNIQUIFIER
PROCS=8

if [[ ! -z "$PACT_PAR" ]] ; then
  PROCS=$PACT_PAR
fi

if [[ $mode == TIME ]]; then
  export DEBUG=0
  export TURBINE_LOG=0
  export TURBINE_DEBUG=0
  export ADLB_DEBUG=0
  export ADLB_PRINT_TIME=true
  time turbine -n$PROCS $tcl "$@" &> $out
  rc=$?
  if [[ $rc == 0 ]]
  then
    grep 'ADLB Total Elapsed Time' $out > $time
    cat $time
  else
    echo "Error: return code $rc"
    exit $rc
  fi
elif [[ $mode == OPCOUNT ]]; then
  export DEBUG=0
  export TURBINE_LOG=0
  export TURBINE_DEBUG=0
  export ADLB_DEBUG=0
  export ADLB_PRINT_TIME=true
  export ADLB_PERF_COUNTERS=true
  time turbine -n$PROCS $tcl "$@" &> $out 
  rc=$?
  if [[ $rc == 0 ]]
  then
  $scriptdir/opcounts.py < $out > $counts
    cat $counts
  else
    echo "Error: return code $rc"
    exit $rc
  fi
else
  time turbine -n$PROCS $tcl "$@"
  rc=$?
  if [[ $rc != 0 ]]
  then
    echo "Error: return code $rc"
    exit $rc
  fi
fi

if [ "$?" -ne 0 ]; then
  echo "Script run failed"
  exit 1
fi
