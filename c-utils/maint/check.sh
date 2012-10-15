#!/bin/bash

# Input: FILE containing list of file names
# Output: Warning for each file name that does not exist

# Used to check for the presence of include files

FILE=$1

for f in $( cat ${FILE} )
do
  test -e ${f} || echo "Not found: ${f}"
done
