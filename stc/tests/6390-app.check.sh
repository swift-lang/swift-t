#!/usr/bin/env bash

if [ -f 6390.txt ]; then
  contents=`cat 6390.txt`
  rm 6390.txt
  if [ $contents != "hello,world" ]; then
    echo "Error: contents of 6390.txt was $contents"
    exit 1
  fi
else
  echo "Error: file 6390.txt was not created."
  exit 1
fi

# cleanup symlink
script=6390-echostderr.sh
if [ -h $script ]; then
    rm $script
fi
