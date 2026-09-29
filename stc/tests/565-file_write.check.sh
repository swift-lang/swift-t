#!/usr/bin/env bash
set -eu

# Strip carriage returns: some MPI launchers forward rank stdout over a
# pty, turning each "\n" into "\r\n", which ".*$" would capture into the
# filename below.
tmpfile=$( tr -d '\r' < "${TURBINE_OUTFILE}" | grep -o 'TMP FILENAME:.*$' )
tmpfile=`echo $tmpfile | sed 's/TMP FILENAME://'`
if [ -f "$tmpfile" ]; then
  echo "Temporary file $tmpfile not deleted!"
  # TODO: currently doesn't pass
  #exit 1
else
  echo "Temporary $tmpfile was correctly deleted!"
fi

rm test.tmp
