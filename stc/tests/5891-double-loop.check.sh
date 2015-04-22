#!/usr/bin/env bash

ROWS=20
COLS=20

STATUS=0
for row in `seq 0 $(($ROWS - 1))`
do
  ROW_REGEX=' row '"${row}"':  0.0000( 1.0000){'"$COLS"'}$'
  matches=`grep -E "$ROW_REGEX" "$TURBINE_OUTPUT" | wc -l`
  if [ "$matches" -eq 1 ]
  then
    :
  else
    echo "row ${row}, ${matches} !=1 matches in ${TURBINE_OUTPUT}"
    STATUS=1
  fi
done
exit $STATUS
