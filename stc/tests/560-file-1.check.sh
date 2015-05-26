#!/bin/sh
set -eu

for outfile in bob.txt bob2.txt
do
  if [ ! -f $outfile ]; then
      echo "$outfile was not created"
      exit 1
  fi

  contents=`cat $outfile`
  if [ "$contents" = "hello world!" ] ; then
      rm $outfile
  else
      echo "$outfile did not have expected contents"
      exit 1
  fi
done
rm alice.txt
