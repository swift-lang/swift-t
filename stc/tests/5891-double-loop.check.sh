#!/usr/bin/env bash

ROWS=20
COLS=20

STATUS=0

# Strip carriage returns: some MPI launchers forward rank stdout over a
# pty, turning each "\n" into "\r\n"
CLEAN=$( mktemp )
trap 'rm -f "${CLEAN}"' EXIT
tr -d '\r' < "$TURBINE_OUTFILE" > "${CLEAN}"

for row in $( seq 0 $[ ROWS - 1 ] )
do
  # No leading space: launchers prefix the line differently, e.g. MPICH
  # gives "[0] row 0:" but OpenMPI gives "[1,0]<stdout>:row 0:"
  ROW_REGEX='row '"${row}"':  0.0000( 1.0000){'"$COLS"'}'
  matches=$( grep -c -E "$ROW_REGEX" "${CLEAN}" )
  if (( $matches == 1 ))
  then
    :
  else
    echo "row ${row}, ${matches} != 1 matches in ${TURBINE_OUTFILE}"
    echo
    echo "output is:"
    cat $CLEAN
    echo "output done."
    STATUS=1
    break
  fi
done

exit $STATUS
