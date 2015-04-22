#!/bin/sh

for outfile in bob.txt bob2.txt
do
  if [ ! -f $outfile ]; then
      echo "$outfile was not created"
      exit 1
  fi

  contents=`cat $outfile`
  if [ "$contents" = "hello world!" ] ; then
      rm $outfile alice.txt
      exit 0
  else
      echo "$outfile did not have expected contents"
      exit 1
  fi
done
