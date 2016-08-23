#!/bin/bash
set -e

# CHECK TOOLS

# Checks for missing system compilers and tools

TOOLS=( ant autoconf make javac mpicc swig zsh )
declare -a MISSING

for T in ${TOOLS[@]}
do
  if ! which ${T} 2>&1 > /dev/null
  then
    MISSING+=( ${T} )
  fi
done

if (( ${#MISSING} ))
then
  echo "This system is missing the following required tools:"
  for T in ${MISSING[@]}
  do
    echo ${T}
  done
  exit 1
fi

exit
