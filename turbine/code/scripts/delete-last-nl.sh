#!/bin/sh
set -eu

# DELETE LAST NL
# Deletes last character of given file
# Modifies file in place
# Just run ./delete-last-nl.sh common.m4
# See common.m4

if [ ${#} != 1 ]
then
  echo "Provide 1 file!"
  exit 1
fi

# set -x
FILE=$1
SIZE=$( stat --format "%s" $FILE )
SIZE=$(( SIZE-1 ))
mv --backup=numbered $FILE $FILE.bak
head --bytes=$SIZE $FILE.bak > $FILE
