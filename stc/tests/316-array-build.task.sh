#!/bin/bash -eu

INPUT=$1
N=$2

if [[ ! -f ${INPUT} ]]
then 
  echo "File not found: ${INPUT}"
  exit 1
fi

for (( i=0 ; $i < $N ; i++ ))
do
  VALUE=$RANDOM
  FILE=test-316-$VALUE.data
  echo $VALUE > $FILE
  echo "wrote: $FILE"
done
