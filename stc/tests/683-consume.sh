#!/usr/bin/env bash
dir=$1
f=$dir/tmp.txt
if [ ! -f $f ]
then
  echo "$f not found"
  exit 1
fi

f_contents=$(cat $f)
if [ "$f_contents" != "TEST" ]
then
  echo "$f didn't have expected contents"
  exit 1
fi
