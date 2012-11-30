#!/usr/bin/env bash
# Poor man's profiler for Turbine http://poormansprofiler.org/
# This uses gdb to sample the execution of a process periodically.
# This allows you to profile Turbine without any special recompilation.
#
# How to use:
# We assume that all processes called tclsh8.5 are Turbine processes.
# We sample stacks from each of these processes and log them to
# <proc name>.<pid>.pmp.stax.  These then get summarized into 
# <proc name>.<pid>.pmp
#

# Process name of tclsh8.5
TCLSH=tclsh8.5

PIDS=$(pidof "$TCLSH")
INTERVAL=0.5

set -e

MAX_SAMPLES=100000
if [ $# -ge 1 ]; then
  MAX_SAMPLES=$1
fi

echo "PMP starting.  Collecting up to $MAX_SAMPLES samples for processes called $TCLSH" >&2

if [ -z "$PIDS" ]; then
  echo "No processes called $TCLSH found" 1>&2
  exit 1
fi

echo "Processes found: $PIDS" 1>&2

# Kill children on signal
trap "kill 0" SIGINT SIGTERM EXIT

for PID in $PIDS; do
  PREFIX=$TCLSH.$PID
  STAX_FILE=$PREFIX.pmp.stax
  OUT_FILE=$PREFIX.pmp
  echo "Logging stacks to $STAX_FILE" 1>&2
  (
    SAMPLES=0
    if [ -f $STAX_FILE ]; then
      echo "$STAX_FILE already exists, appending to it!" >&2
    fi
    # keep running until failure (prob. because process went away)
    while [[ $SAMPLES -lt $MAX_SAMPLES ]] ; do
      if [ ! -d /proc/$PID ]; then
        break 
      fi
      if gdb -ex "set pagination 0" -ex "thread apply all bt" \
           --batch -p $PID ; then
        :
      else
        # Sometimes get spurious errors
        :
      fi 
      SAMPLES=$(($SAMPLES + 1))
      sleep $INTERVAL
    done
    echo "Done with $PID" 1>&2
    awk '
        BEGIN { s = ""; }
         /^Thread/ { print s; s = ""; }
         /^\#/ { if (s != "" ) { s = s "," $4} else { s = $4 } } 
         END { print s }' < ${STAX_FILE} | \
         sort | uniq -c | sort -r -n -k 1,1 > $OUT_FILE
    echo "Output file for $PID at $OUT_FILE" 1>&2
  ) >> $STAX_FILE &
done

# Wait for all profilers to finish
wait

echo DONE.

