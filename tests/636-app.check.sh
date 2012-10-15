#!/bin/sh
OUT=636-out.txt
if [ ! -f $OUT ]; then
  echo "$OUT not created"
  exit 1
fi

contents=`cat $OUT`
if [ "$contents" = "hello" ]; then
  rm ${OUT}
else
  echo "Contents of $OUT not right"
  exit 1
fi
