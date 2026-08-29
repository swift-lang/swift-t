#!/bin/sh
# The point of a file argument: the named file is really there afterwards,
# holding what the program wrote to it.
set -eu

check()
{
  file=$1
  expected=$2
  if [ ! -f "${file}" ]
  then
    echo "${file} was not created"
    exit 1
  fi
  contents=$( cat "${file}" )
  if [ "${contents}" != "${expected}" ]
  then
    echo "${file} holds '${contents}', expected '${expected}'"
    exit 1
  fi
  rm "${file}"
}

check 7195-output-1.a.out written-to-a
check 7195-output-1.b.out written-to-b
