#!/bin/sh
set -eu

for file in 7196-output-2.a.out 7196-output-2.b.out
do
  if [ ! -f "${file}" ]
  then
    echo "${file} was not created"
    exit 1
  fi
  rm "${file}"
done
