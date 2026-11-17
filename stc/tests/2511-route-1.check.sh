#!/bin/sh
set -e

# Each pattern/match branch runs once per matching key, and default
# runs only for the key that no other branch took.
# Strip CRs: some launchers end lines with "\r\n".
OUT=$( tr -d '\r' < $TURBINE_OUTFILE )
count()
{
  printf '%s\n' "$OUT" | grep -c "trace: $1"
}
[ $( count 'pattern:' ) = 3 ] || { echo "pattern count"; exit 1; }
[ $( count 'match:'   ) = 3 ] || { echo "match count";   exit 1; }
[ $( count 'default:' ) = 1 ] || { echo "default count"; exit 1; }
