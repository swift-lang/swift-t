#!/bin/bash

N=$1

for (( i=0 ; $i < $N ; i++ ))
do
  VALUE=$RANDOM
  FILE=test-316-$VALUE.data
  echo $VALUE > $FILE
  echo "wrote: $FILE"
done
