#!/bin/bash

# MAKE-DATA

N=$1
OUTPUT=$2

source helpers.sh

nonempty N
nonempty OUTPUT

touch ${OUTPUT}    ; check "Could not create output: ${OUTPUT}"

for (( i=0 ; i<N ; i++ ))
do
  echo "123456789" 
done > ${OUTPUT}

exit 0
