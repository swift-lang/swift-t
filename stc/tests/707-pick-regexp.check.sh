#!/bin/sh
set -e

# Count only the values after "trace: ".  The launcher prefixes each
# line differently (MPICH "[0] ", OpenMPI "[1,0]<stdout>:"), so counting
# words in the whole line would count the prefix too.  Strip CRs: some
# launchers forward rank stdout over a pty, ending lines with "\r\n".
N=$( tr -d '\r' < $TURBINE_OUTFILE | grep -o 'trace: .*' | sed 's/^trace: //' | wc -w )
if [ $N != 2 ]
then
  echo "Should have 2 values on 'trace:' line, got $N !"
  exit 1
fi
