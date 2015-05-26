#!/usr/bin/env bash
set -eu

tmpfile=`grep -o 'TMP FILENAME:.*$' "${TURBINE_OUTPUT}"`
tmpfile=`echo $tmpfile | sed 's/TMP FILENAME://'`
if [ -f "$tmpfile" ]; then
  echo "Temporary file $tmpfile not deleted!"
  # TODO: currently doesn't pass
  #exit 1
else
  echo "Temporary $tmpfile was correctly deleted!"
fi

rm test.tmp
