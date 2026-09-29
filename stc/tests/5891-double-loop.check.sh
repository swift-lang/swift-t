#!/usr/bin/env bash

ROWS=20
COLS=20

STATUS=0

# Strip carriage returns: some MPI launchers forward rank stdout over a
# pty, turning each "\n" into "\r\n", which defeats the "$" anchor below.
CLEAN=$( mktemp )
trap 'rm -f "${CLEAN}"' EXIT
tr -d '\r' < "$TURBINE_OUTFILE" > "${CLEAN}"

for row in `seq 0 $(($ROWS - 1))`
do
  ROW_REGEX=' row '"${row}"':  0.0000( 1.0000){'"$COLS"'}$'
  matches=`grep -c -E "$ROW_REGEX" "${CLEAN}"`
  if [ "$matches" -eq 1 ]
  then
    :
  else
    echo "row ${row}, ${matches} !=1 matches in ${TURBINE_OUTFILE}"
    STATUS=1
  fi
done
exit $STATUS
