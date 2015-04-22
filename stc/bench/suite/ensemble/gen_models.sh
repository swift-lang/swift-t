#!/bin/sh

for i in `seq 10`
do
  FILE=model$i.data
  head -c 64K /dev/urandom > $FILE
  echo $FILE created
done
