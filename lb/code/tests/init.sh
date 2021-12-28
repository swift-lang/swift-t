#!/bin/bash
set -eux

THIS=$0
EXEC=${THIS%.sh}.x
OUTPUT=${THIS%.sh}.out

if mpiexec -n 4 ${EXEC} > ${OUTPUT} 2>&1
then
  CODE=0
  echo "OK"
else
  CODE=$?
  echo "FAILED: CODE=$CODE"
  cat $OUTPUT
fi

exit $CODE
