#!/bin/bash

FILE=$1

for f in $( cat ${FILE} )
do
  test -e ${f} || echo "Not found: ${f}"
done
